package org.mikusch.service;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import net.dv8tion.jda.api.entities.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface ThinkerService {

    CompletableFuture<Webhook> getThinker(Guild guild);

    Double getFrequency(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, boolean immediate);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook);

    CompletableFuture<ReadonlyMessage> triggerThinker(Guild guild, Webhook webhook, boolean immediate);

    CompletableFuture<Message> retrieveRandomMessage(Guild guild);

    boolean isValidMessage(Message message);

    boolean isValidChannel(IPermissionContainer channel);

    void saveMessage(Message message);

    void saveAllMessagesInChannel(MessageChannel channel);

    void deleteMessage(long messageId);

    void deleteMessages(Collection<Long> messageIds);

    void deleteAllMessagesFromChannel(Channel channel);
}
