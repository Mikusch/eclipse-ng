package org.mikusch.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TeamMember;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.stream.Collectors;

@Component
public class AboutCommand implements EclipseCommand {

    @Override
    public CommandData getCommandData() {
        return new CommandData("about", "Tells you more about the bot");
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.getJDA().retrieveApplicationInfo().queue(info -> {
            String inviteUrl = event.getJDA().getInviteUrl(Permission.MESSAGE_ADD_REACTION, Permission.VIEW_CHANNEL, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_HISTORY, Permission.MESSAGE_EXT_EMOJI, Permission.NICKNAME_CHANGE);
            MessageEmbed embed = new EmbedBuilder()
                    .setThumbnail(info.getIconUrl())
                    .setTitle(info.getName(), info.isBotPublic() ? inviteUrl : null)
                    .appendDescription(info.getDescription())
                    .addField("Creator", info.getTeam() != null ? info.getTeam().getMembers().stream().filter(member -> member.getMembershipState() == TeamMember.MembershipState.ACCEPTED).map(TeamMember::getUser).map(IMentionable::getAsMention).collect(Collectors.joining(" ")) : info.getOwner().getAsMention(), true)
                    .addField("Library", String.format("JDA %s (Java %s)", JDAInfo.VERSION, System.getProperty("java.specification.version")), true)
                    .addField("Uptime", DurationFormatUtils.formatDurationWords(ManagementFactory.getRuntimeMXBean().getUptime(), true, true), true)
                    .setFooter("Created", null)
                    .setTimestamp(info.getTimeCreated())
                    .build();

            event.getHook().editOriginalEmbeds(embed).queue();
        });
    }
}
