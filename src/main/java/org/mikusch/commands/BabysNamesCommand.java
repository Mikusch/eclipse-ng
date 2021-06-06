package org.mikusch.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;
import org.mikusch.util.TwitterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class BabysNamesCommand implements EclipseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(BabysNamesCommand.class);
    private static final long BABYSNAMES_ACCOUNT_ID = 1592227514;

    private final Set<String> names = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    public BabysNamesCommand(TwitterService twitterService) {
        //Periodically fetch all baby names from the @Babysnames twitter
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                twitterService.getAllStatusesForUser(BABYSNAMES_ACCOUNT_ID).stream()
                        .filter(status -> !status.isRetweet()) // no retweets
                        .filter(status -> status.getUserMentionEntities().length == 0) // no user mentions
                        .filter(status -> !status.getText().startsWith("@")) // catch unresolved mentions too
                        .filter(status -> status.getText().length() <= 32) // only names that fit the character limit
                        .filter(status -> status.getURLEntities().length == 0) // no URLs
                        .filter(status -> status.getMediaEntities().length == 0) // no media
                        .map(Status::getText)
                        .forEach(names::add);
            } catch (TwitterException e) {
                LOG.error("Failed to fetch statuses", e);
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public CommandData getCommandData() {
        return new CommandData("babyname", "Gives you a good old baby name!");
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        var guild = Objects.requireNonNull(event.getGuild());
        var member = Objects.requireNonNull(event.getMember());

        String name = names.stream().skip(new Random().nextInt(names.size())).findAny().orElseThrow();
        if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
            member.modifyNickname(name).queue(modified -> event.getHook().editOriginal(MessageFormat.format("Your new nickname is **{0}**.", name)).queue());
        } else {
            event.getHook().editOriginal(MessageFormat.format("I was unable to set your nickname to **{0}**.", name)).queue();
        }
    }
}
