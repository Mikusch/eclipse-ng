package org.mikusch.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MiscUtil;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.ThinkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ThinkerListener extends ListenerAdapter {

    private final JdbcTemplate jdbcTemplate;
    private final ThinkerService thinkerService;

    @Autowired
    public ThinkerListener(JdbcTemplate jdbcTemplate, ThinkerService thinkerService, JDA jda) {
        this.jdbcTemplate = jdbcTemplate;
        this.thinkerService = thinkerService;
        jda.addEventListener(this);
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;
        if (event.getMessage().getType() != MessageType.DEFAULT && event.getMessage().getType() != MessageType.INLINE_REPLY)
            return;

        thinkerService.getThinker(event.getGuild()).thenAccept(webhook -> {
            if (webhook != null) {
                thinkerService.saveMessage(event.getMessage());
            }
        });
    }

    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        thinkerService.deleteMessage(event.getMessageIdLong());
    }

    @Override
    public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
        thinkerService.deleteMessages(event.getMessageIds().stream().map(MiscUtil::parseSnowflake).collect(Collectors.toList()));
    }

    @Override
    public void onTextChannelDelete(@NotNull TextChannelDeleteEvent event) {
        thinkerService.deleteAllMessagesFromChannel(event.getChannel());
    }
}
