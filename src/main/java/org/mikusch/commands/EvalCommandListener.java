package org.mikusch.commands;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvalCommandListener extends ListenerAdapter {

    @Autowired
    public EvalCommandListener(JDA jda) {
        jda.addEventListener(this);
        jda.upsertCommand(Commands.slash("eval", "Evaluates code")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                        .addOption(OptionType.STRING, "code", "The code to evaluate", true))
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("eval")) return;

        event.deferReply().queue(hook -> {
            if (event.getUser().getIdLong() != 177414298698645504L)
                hook.editOriginal("This command is not available to you.").queue();

            var binding = new Binding();
            var shell = new GroovyShell(binding);
            binding.setProperty("event", event);

            Object result = null;
            try {
                OptionMapping code = event.getOption("code");
                if (code != null) {
                    result = shell.evaluate(code.getAsString());
                }
            } catch (Exception e) {
                result = e.toString();
            } finally {
                // Max. 2000 characters with room for markdown
                hook.editOriginal(MarkdownUtil.codeblock(StringUtils.abbreviate(String.valueOf(result), 1990))).queue();
            }
        });
    }
}
