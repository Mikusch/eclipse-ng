package org.mikusch.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

public interface EclipseCommand {

    CommandData getCommandData();

    void onSlashCommand(@NotNull SlashCommandEvent event);
}
