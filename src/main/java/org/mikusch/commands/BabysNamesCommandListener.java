package org.mikusch.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.TwitterService;
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
public class BabysNamesCommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(BabysNamesCommandListener.class);
    private static final long BABYSNAMES_ACCOUNT_ID = 1592227514;

    private final List<String> names = Collections.synchronizedList(new ArrayList<>());

    @Autowired
    public BabysNamesCommandListener(JDA jda, TwitterService twitterService) {
        jda.addEventListener(this);
        jda.upsertCommand(new CommandData("babyname", "Gives you a good old baby name!")).queue();

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
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (!event.getName().equals("babyname")) return;

        var guild = Objects.requireNonNull(event.getGuild());
        var member = Objects.requireNonNull(event.getMember());

        event.deferReply().queue(hook -> {
            if (!names.isEmpty()) {
                String name = names.get(new Random().nextInt(names.size()));
                if (PermissionUtil.canInteract(guild.getSelfMember(), member)) {
                    member.modifyNickname(name).queue(modified -> event.getHook().editOriginal(MessageFormat.format("Your new nickname is **{0}**.", name)).queue());
                } else {
                    hook.editOriginal(MessageFormat.format("I was unable to set your nickname to **{0}**.", name)).queue();
                }
            } else {
                hook.editOriginal("This command is not ready yet. Please try again later.").queue();
            }
        });
    }
}
