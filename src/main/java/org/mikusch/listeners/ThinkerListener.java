package org.mikusch.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MiscUtil;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.ThinkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ThinkerListener extends ListenerAdapter {

    private final ThinkerService thinkerService;

    @Autowired
    public ThinkerListener(ThinkerService thinkerService, JDA jda) {
        this.thinkerService = thinkerService;
        jda.addEventListener(this);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            var message = event.getMessage();
            if (thinkerService.isValidMessage(message)) {
                thinkerService.getThinker(event.getGuild()).thenAccept(webhook -> {
                    if (webhook != null) {
                        thinkerService.saveMessage(message);
                    }
                });
            }
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (event.isFromGuild()) {
            thinkerService.deleteMessage(event.getMessageIdLong());
        }
    }

    @Override
    public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
        thinkerService.deleteMessages(event.getMessageIds().stream().map(MiscUtil::parseSnowflake).toList());
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (event.getChannelType().isMessage()) {
            thinkerService.deleteAllMessagesFromChannel(event.getChannel());
        }
    }
}
