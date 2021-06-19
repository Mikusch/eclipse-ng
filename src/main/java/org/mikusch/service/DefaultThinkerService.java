package org.mikusch.service;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
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
        }, 0, 1, TimeUnit.MINUTES);
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
        return triggerThinker(guild, false);
    }

    @Override
    public CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, boolean immediate) {
        return getThinker(guild).thenCompose(webhook -> triggerThinker(guild, webhook, immediate));
    }

    @Override
    public CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook) {
        return triggerThinker(guild, webhook, false);
    }

    public CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook, boolean immediate) {
        if (immediate) {
            return triggerThinkerImmediate(guild, webhook);
        } else {
            var channel = webhook.getChannel();
            var lastPostedTime = lastPostedTimes.computeIfAbsent(webhook.getIdLong(), webhookId -> OffsetDateTime.now());

            return channel.getHistory()
                    .retrievePast(100)
                    .submit()
                    .thenApply(messages -> messages.stream().filter(this::isValidMessage).map(ISnowflake::getTimeCreated).collect(Collectors.toList()))
                    .thenApply(timestamps -> {
                        //Calculate the average interval between messages sent in the current channel
                        double avgMillis = timestamps.stream().mapToLong(timestamp -> Duration.between(timestamp, lastPostedTime).toMillis()).average().orElse(0);
                        return Duration.ofMillis((long) avgMillis);
                    })
                    .thenCompose(duration -> {
                        if (lastPostedTime.plus(duration).isBefore(OffsetDateTime.now())) {
                            return triggerThinkerImmediate(guild, webhook);
                        } else {
                            LOGGER.debug("The next Thinker for {} will be triggered around {}", guild, lastPostedTime.plus(duration));
                            return CompletableFuture.completedFuture(null);
                        }
                    });
        }
    }

    private CompletableFuture<ReadonlyMessage> triggerThinkerImmediate(Guild guild, Webhook webhook) {
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
    public boolean isValidMessage(Message message) {
        return isValidChannel(message.getTextChannel()) && !message.getAuthor().isBot() && !message.isWebhookMessage() && (message.getType() == MessageType.DEFAULT || message.getType() == MessageType.INLINE_REPLY);
    }

    @Override
    public boolean isValidChannel(TextChannel channel) {
        var override = channel.getPermissionOverride(channel.getGuild().getPublicRole());
        return override == null || !override.getDenied().contains(Permission.MESSAGE_READ);
    }

    @Override
    public void saveMessage(Message message) {
        jdbcTemplate.update(
                "INSERT INTO `thoughts` (`guild_id`, `channel_id`, `user_id`, `message_id`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE `guild_id` = `guild_id`",
                message.getGuild().getIdLong(), message.getChannel().getIdLong(), message.getAuthor().getIdLong(), message.getIdLong()
        );
    }

    @Override
    public void saveAllMessagesInChannel(MessageChannel channel) {
        channel.getIterableHistory().forEachAsync(value -> {
            if (isValidMessage(value)) {
                saveMessage(value);
            }
            return true;
        });
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
    public void deleteAllMessagesFromChannel(MessageChannel channel) {
        jdbcTemplate.update("DELETE FROM `thoughts` WHERE `channel_id` = ?", channel.getIdLong());
    }
}
