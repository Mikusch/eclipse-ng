package org.mikusch.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mikusch.service.ThinkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ThinkerCommandListener extends ListenerAdapter {

    private final ThinkerService thinkerService;

    @Autowired
    public ThinkerCommandListener(ThinkerService thinkerService, JDA jda) {
        this.thinkerService = thinkerService;
        jda.addEventListener(this);
        jda.getGuildById(517094597030314004L).upsertCommand(
                new CommandData("thinker", "The Thinker").addSubcommands(
                        new SubcommandData("force", "Forces the Thinker to think")
                )
        ).queue();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (!event.getName().equals("thinker")) return;

        var member = Objects.requireNonNull(event.getMember());

        event.deferReply().queue(hook -> {
            if (StringUtils.equals(event.getSubcommandName(), "force")) {
                if (member.hasPermission(Permission.MANAGE_WEBHOOKS)) {
                    thinkerService.triggerThinker(event.getGuild(), true).thenAccept(readonlyMessage -> {
                        if (readonlyMessage.getChannelId() == event.getChannel().getIdLong()) {
                            hook.deleteOriginal().queue();
                        } else {
                            hook.editOriginal("The Thinker has spoken in " + event.getGuild().getTextChannelById(readonlyMessage.getChannelId()).getAsMention() + ".").queue();
                        }
                    });
                } else {
                    hook.editOriginal("You need the " + MarkdownUtil.bold(Permission.MANAGE_WEBHOOKS.getName()) + " permission in this server to use this command.").queue();
                }
            }
        });
    }
}
