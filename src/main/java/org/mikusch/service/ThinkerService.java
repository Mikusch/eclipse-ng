package org.mikusch.service;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Webhook;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface ThinkerService {

    CompletableFuture<Webhook> getThinker(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, boolean force);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook, boolean force);

    CompletableFuture<Message> retrieveRandomMessage(Guild guild);

    boolean isValidMessage(Message message);

    void saveMessage(Message message);

    void saveAllMessagesInChannel(MessageChannel channel);

    void deleteMessage(long messageId);

    void deleteMessages(Collection<Long> messageIds);

    void deleteAllMessagesFromChannel(MessageChannel channel);
}
