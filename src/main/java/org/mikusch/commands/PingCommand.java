package org.mikusch.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class PingCommand implements EclipseCommand {

    @Override
    public CommandData getCommandData() {
        return new CommandData("ping", "Shows the ping");
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.getJDA().getRestPing().queue(ping -> event.reply("\uD83C\uDFD3 Ping: ``" + ping + " ms`` | Websocket: ``" + event.getJDA().getGatewayPing() + " ms``").queue());
    }
}
