package org.mikusch.commands;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class EvalCommand implements EclipseCommand, PostCommandAddAction {

    @Override
    public CommandData getCommandData() {
        return new CommandData("eval", "Evaluates code")
                .setDefaultEnabled(false)
                .addOption(OptionType.STRING, "code", "The code to evaluate", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
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
            //Max. 2000 characters with room for markdown
            event.getHook().editOriginal(MarkdownUtil.codeblock(StringUtils.abbreviate(String.valueOf(result), 1990))).queue();
        }
    }

    @Override
    public void onCommandAdded(@NotNull Command command) {
        //Restrict command to Mikusch#0001
        var guild = command.getJDA().getGuildById(186809082470989824L);
        if (guild != null) {
            guild.updateCommandPrivilegesById(command.getIdLong(), new CommandPrivilege(CommandPrivilege.Type.USER, true, 177414298698645504L)).queue();
        }
    }
}
