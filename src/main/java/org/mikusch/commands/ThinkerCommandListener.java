package org.mikusch.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.ThinkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ThinkerCommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ThinkerCommandListener.class);

    private final ThinkerService thinkerService;

    @Autowired
    public ThinkerCommandListener(ThinkerService thinkerService, JDA jda) {
        this.thinkerService = thinkerService;
        jda.addEventListener(this);
        jda.upsertCommand(
                Commands.slash("thinker", "The Thinker").addSubcommands(
                        new SubcommandData("force", "Forces the Thinker to think"),
                        new SubcommandData("scan", "Scans all messages in a channel and stores them in the database")
                                .addOption(OptionType.CHANNEL, "channel", "The channel to scan in")
                )
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("thinker")) return;

        var member = Objects.requireNonNull(event.getMember());

        event.deferReply().queue(hook -> {
            if (member.hasPermission(Permission.MANAGE_WEBHOOKS)) {
                if (StringUtils.equals(event.getSubcommandName(), "force")) {
                    thinkerService.triggerThinker(event.getGuild(), true).thenAccept(readonlyMessage -> {
                        if (readonlyMessage.getChannelId() == event.getChannel().getIdLong()) {
                            hook.deleteOriginal().queue();
                        } else {
                            hook.editOriginal("The Thinker has spoken in " + event.getGuild().getTextChannelById(readonlyMessage.getChannelId()).getAsMention() + ".").queue();
                        }
                    }).exceptionally(e -> {
                        LOG.error("Failed to trigger Thinker", e);

                        hook.editOriginal("The Thinker is currently asleep (an internal error has occurred).").queue();
                        return null;
                    });
                } else if (StringUtils.equals(event.getSubcommandName(), "scan")) {
                    var channelOption = event.getOption("channel");
                    if (channelOption != null) {
                        var messageChannel = channelOption.getAsMessageChannel();
                        if (messageChannel != null) {
                            hook.editOriginal("Scanning " + channelOption.getAsGuildChannel().getAsMention() + " for messages...").queue(message -> {
                                thinkerService.saveAllMessagesInChannel(messageChannel);
                            });
                        } else {
                            hook.editOriginal(channelOption.getAsGuildChannel().getAsMention() + " is not a message channel!").queue();
                        }
                    } else {
                        hook.editOriginal("Scanning this channel for messages...").queue(message -> {
                            thinkerService.saveAllMessagesInChannel(event.getTextChannel());
                        });
                    }
                }
            } else {
                hook.editOriginal("You need the " + MarkdownUtil.bold(Permission.MANAGE_WEBHOOKS.getName()) + " permission in this server to use this command.").queue();
            }
        });
    }
}
