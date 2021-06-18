package org.mikusch.service;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class DefaultThinkerService implements ThinkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultThinkerService.class);

    private final ConcurrentHashMap<Long, OffsetDateTime> lastPostedTimes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DefaultThinkerService(JdbcTemplate jdbcTemplate, JDA jda) {
        this.jdbcTemplate = jdbcTemplate;

        executor.scheduleAtFixedRate(() -> {
            var sqlRowSet = jdbcTemplate.queryForRowSet("SELECT `guild_id`, `webhook_id` FROM `thinkers`");
            while (sqlRowSet.next()) {
                var guild = jda.getGuildById(sqlRowSet.getLong("guild_id"));
                if (guild != null) {
                    jda.retrieveWebhookById(sqlRowSet.getLong("webhook_id")).queue(webhook -> triggerThinker(guild, webhook));
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Webhook> getThinker(Guild guild) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT `webhook_id` FROM `thinkers` WHERE `guild_id` = ?",
                    (rs, rowNum) -> guild.getJDA().retrieveWebhookById(rs.getLong("webhook_id")).submit(),
                    guild.getIdLong()
            );
        } catch (EmptyResultDataAccessException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild) {
        return getThinker(guild).thenCompose(webhook -> triggerThinker(guild, webhook));
    }

    @Override
    public CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook) {
        var channel = webhook.getChannel();
        var lastPostedTime = lastPostedTimes.computeIfAbsent(webhook.getIdLong(), webhookId -> OffsetDateTime.now());

        return channel.getHistory()
                .retrievePast(100)
                .map(messages -> messages.stream().map(ISnowflake::getTimeCreated).collect(Collectors.toList()))
                .map(timestamps -> {
                    //Calculate the average interval between messages sent in the current channel
                    double avgMillis = timestamps.stream().mapToLong(timestamp -> Duration.between(timestamp, lastPostedTime).toMillis()).average().orElseThrow();
                    return Duration.ofMillis((long) avgMillis);
                })
                .submit()
                .thenCompose(duration -> {
                    if (lastPostedTime.plus(duration).isBefore(OffsetDateTime.now())) {
                        var client = WebhookClientBuilder.fromJDA(webhook).buildJDA();
                        return retrieveRandomMessage(guild).thenCompose(message -> client.send(message).whenComplete((readonlyMessage, throwable) -> {
                            //If there was no error, remember the time we sent the message
                            if (throwable == null) {
                                lastPostedTimes.put(webhook.getIdLong(), OffsetDateTime.now());
                            } else {
                                LOGGER.error("Exception encountered while sending Thinker message", throwable);
                            }

                            //And finally, close the client
                            client.close();
                        }));
                    } else {
                        LOGGER.debug("Thinker for {} will trigger at {}", guild, lastPostedTime.plus(duration));
                        return CompletableFuture.completedFuture(null);
                    }
                });
    }

    @Override
    public CompletableFuture<Message> retrieveRandomMessage(Guild guild) {
        return jdbcTemplate.queryForObject(
                "SELECT `channel_id`, `message_id` FROM `thoughts` WHERE `guild_id` = ? ORDER BY RAND() LIMIT 1",
                (rs, rowNum) -> guild.getJDA().getTextChannelById(rs.getLong("channel_id")).retrieveMessageById(rs.getLong("message_id")).submit(),
                guild.getIdLong()
        );
    }

    @Override
    public void saveMessage(Message message) {
        jdbcTemplate.update(
                "INSERT INTO `thoughts` (`guild_id`, `channel_id`, `user_id`, `message_id`) VALUES (?, ?, ?, ?)",
                message.getGuild().getIdLong(), message.getChannel().getIdLong(), message.getAuthor().getIdLong(), message.getIdLong()
        );
    }

    @Override
    public void deleteMessage(long messageId) {
        jdbcTemplate.update("DELETE FROM `thoughts` WHERE `message_id` = ?", messageId);
    }

    @Override
    public void deleteMessages(Collection<Long> messageIds) {
        jdbcTemplate.update("DELETE FROM `thoughts` WHERE `message_id` IN (:ids)", new MapSqlParameterSource("ids", messageIds));
    }

    @Override
    public void deleteAllMessagesFromChannel(TextChannel channel) {
        jdbcTemplate.update("DELETE FROM `thoughts` WHERE `channel_id` = ?", channel.getIdLong());
    }
}
