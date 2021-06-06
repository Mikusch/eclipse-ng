package org.mikusch;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.mikusch.commands.EclipseCommand;
import org.mikusch.commands.PostCommandAddAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CommandListener.class);

    private final List<EclipseCommand> commands;

    @Autowired
    public CommandListener(JDA jda, List<EclipseCommand> commands) {
        this.commands = commands;

        jda.addEventListener(this);
        jda.updateCommands()
                .addCommands(commands.stream().map(EclipseCommand::getCommandData).collect(Collectors.toList()))
                .queue(slashCommands -> {
                    slashCommands.forEach(slashCommand -> {
                        //Run post-register actions for commands
                        commands.stream()
                                .filter(command -> command.getCommandData().getName().equals(slashCommand.getName()))
                                .filter(PostCommandAddAction.class::isInstance)
                                .map(PostCommandAddAction.class::cast)
                                .findFirst()
                                .ifPresent(command -> command.onCommandAdded(slashCommand));
                    });
                });
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        commands.stream()
                .filter(command -> command.getCommandData().getName().equals(event.getName()))
                .findFirst()
                .ifPresentOrElse(command -> command.onSlashCommand(event), () -> LOG.error("Unknown command: {}", event.getName()));
    }
}
