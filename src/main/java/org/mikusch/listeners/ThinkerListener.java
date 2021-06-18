package org.mikusch.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MiscUtil;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.ThinkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ThinkerListener extends ListenerAdapter {

    private final ThinkerService thinkerService;

    @Autowired
    public ThinkerListener(ThinkerService thinkerService, JDA jda) {
        this.thinkerService = thinkerService;
        jda.addEventListener(this);
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        var message = event.getMessage();
        if (thinkerService.isValidMessage(message)) {
            thinkerService.getThinker(event.getGuild()).thenAccept(webhook -> {
                if (webhook != null) {
                    thinkerService.saveMessage(message);
                }
            });
        }
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
