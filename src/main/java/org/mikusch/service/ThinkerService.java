package org.mikusch.service;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.Webhook;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface ThinkerService {

    CompletableFuture<Webhook> getThinker(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook);

    CompletableFuture<Message> retrieveRandomMessage(Guild guild);

    void saveMessage(Message message);

    void deleteMessage(long messageId);

    void deleteMessages(Collection<Long> messageIds);

    void deleteAllMessagesFromChannel(TextChannel channel);
}
