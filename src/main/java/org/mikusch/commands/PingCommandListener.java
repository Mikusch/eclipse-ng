package org.mikusch.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PingCommandListener extends ListenerAdapter {

    @Autowired
    public PingCommandListener(JDA jda) {
        jda.addEventListener(this);
        jda.getGuildById(186809082470989824L).upsertCommand(new CommandData("ping", "Shows the ping")).queue();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (!event.getName().equals("ping")) return;

        event.deferReply().queue(hook -> event.getJDA().getRestPing().queue(ping -> hook.editOriginal("\uD83C\uDFD3 Ping: ``" + ping + " ms`` | Websocket: ``" + event.getJDA().getGatewayPing() + " ms``").queue()));
    }
}
