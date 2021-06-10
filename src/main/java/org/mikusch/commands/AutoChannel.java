package org.mikusch.commands;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateBitrateEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateUserLimitEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AutoChannel extends ListenerAdapter {
    private static final EnumSet<Permission> CHANNEL_AUTHOR_PERMISSIONS_ALLOW = EnumSet.of(
            Permission.MANAGE_CHANNEL, Permission.PRIORITY_SPEAKER, Permission.VOICE_SPEAK,
            Permission.VOICE_MOVE_OTHERS, Permission.VOICE_USE_VAD, Permission.VOICE_STREAM
    );
    private static final EnumSet<Permission> CHANNEL_AUTHOR_PERMISSIONS_DENY = EnumSet.noneOf(Permission.class);

    private final ListMultimap<Long, Long> activeChannels = Multimaps.synchronizedListMultimap(ArrayListMultimap.create()); // ListMultimap<Guild ID, Voice Channel ID>
    private final Map<Long, Long> channelAuthors = new ConcurrentHashMap<>();   // Map<Voice Channel ID, User ID>
    private final List<Long> customRenamedChannels = Collections.synchronizedList(new ArrayList<>());   // List<Voice Channel ID>

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AutoChannel(JdbcTemplate jdbcTemplate, JDA jda) {
        this.jdbcTemplate = jdbcTemplate;
        jda.addEventListener(this);
    }

    @Override
    public void onVoiceChannelDelete(@Nonnull VoiceChannelDeleteEvent event) {
        var guild = event.getGuild();
        var vc = event.getChannel();
        if (activeChannels.containsValue(vc.getIdLong())) {
            // an auto channel was deleted, remove it from our maps
            activeChannels.remove(guild.getIdLong(), vc.getIdLong());
            channelAuthors.remove(vc.getIdLong());
            customRenamedChannels.remove(vc.getIdLong());
            this.getActiveAutoChannelsForGuild(event.getGuild())
                    .filter(v -> !customRenamedChannels.contains(v.getIdLong()))
                    .forEach(v -> v.getManager().setName(this.createChannelNameFromConnectedMembers(v)).queue());
        } else if (vc.equals(this.getRootAutoChannel(event.getGuild()))) {
            // the root channel was deleted, delete all auto channels (this should happen very rarely)
            // this will trigger the other if-branch above
            activeChannels.get(guild.getIdLong()).forEach(id -> event.getJDA().getVoiceChannelById(id).delete().queue());
        }
    }

    @Nonnull
    @CheckReturnValue
    public Stream<VoiceChannel> getActiveAutoChannelsForGuild(Guild guild) {
        return activeChannels.get(guild.getIdLong()).stream().map(guild::getVoiceChannelById);
    }

    @Nonnull
    public String createChannelNameFromConnectedMembers(VoiceChannel vc) {
        // Map<Activity Name, Count>
        Map<String, Long> activities = vc.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .flatMap(member -> member.getActivities().stream())
                .filter(activity -> activity.getType() != Activity.ActivityType.CUSTOM_STATUS)
                .collect(Collectors.groupingBy(Activity::getName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        int index = this.getActiveAutoChannelsForGuild(vc.getGuild()).collect(Collectors.toList()).indexOf(vc);
        StringBuilder sb = new StringBuilder(String.format("#%d ", index + 1));
        if (activities.isEmpty()) {
            // One activity ex. "Team Fortress 2" or no activity ex. "General"
            sb.append("[").append(this.getDefaultChannelName(vc.getGuild()).orElse("General")).append("]");
        } else {
            // Multiple activities ex. "Team Fortress 2, No Man's Sky, Spotify"
            sb.append(activities.keySet().stream().limit(3).collect(Collectors.joining(", ", "[", "]")));
        }

        return StringUtils.abbreviate(sb.toString(), "...]", 100);
    }

    @Nullable
    public VoiceChannel getRootAutoChannel(Guild guild) {
        var list = jdbcTemplate.queryForList("SELECT `root_channel_id` FROM `autochannels` WHERE `guild_id` = ?", guild.getIdLong());
        return list.stream().findFirst().map(map -> (long) map.get("root_channel_id")).map(guild::getVoiceChannelById).orElse(null);
    }

    @Nonnull
    public Optional<String> getDefaultChannelName(Guild guild) {
        var list = jdbcTemplate.queryForList("SELECT `default_name` FROM `autochannels` WHERE `guild_id` = ?", guild.getIdLong());
        return list.stream().findFirst().map(map -> (String) map.get("default_name"));
    }

    @Override
    public void onVoiceChannelUpdateName(@Nonnull VoiceChannelUpdateNameEvent event) {
        if (activeChannels.containsValue(event.getChannel().getIdLong()) && !event.getNewName().equals(this.createChannelNameFromConnectedMembers(event.getChannel()))) {
            // someone renamed this channel, don't auto-update it anymore
            customRenamedChannels.add(event.getChannel().getIdLong());
        }
    }

    @Override
    public void onVoiceChannelUpdateUserLimit(@Nonnull VoiceChannelUpdateUserLimitEvent event) {
        if (event.getChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
            this.getActiveAutoChannelsForGuild(event.getGuild())
                    .filter(vc -> event.getOldUserLimit() == vc.getUserLimit())
                    .forEach(vc -> vc.getManager().setUserLimit(event.getNewUserLimit()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
        }
    }

    @Override
    public void onVoiceChannelUpdateBitrate(@Nonnull VoiceChannelUpdateBitrateEvent event) {
        if (event.getChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
            this.getActiveAutoChannelsForGuild(event.getGuild())
                    .filter(vc -> event.getOldBitrate() == vc.getBitrate())
                    .forEach(vc -> vc.getManager().setBitrate(event.getNewBitrate()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
        }
    }

    @Override
    public void onPermissionOverrideUpdate(@Nonnull PermissionOverrideUpdateEvent event) {
        if (event.getChannelType() == ChannelType.VOICE && event.getVoiceChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
            this.getActiveAutoChannelsForGuild(event.getGuild()).forEach(vc ->
            {
                vc.getManager()
                        .sync(event.getChannel())
                        .putPermissionOverride(event.getGuild().getMemberById(channelAuthors.get(vc.getIdLong())), CHANNEL_AUTHOR_PERMISSIONS_ALLOW, CHANNEL_AUTHOR_PERMISSIONS_DENY)
                        .reason("Synced permissions with root channel")
                        .queue();
            });
        }
    }

    @Override
    public void onVoiceChannelUpdateParent(@Nonnull VoiceChannelUpdateParentEvent event) {
        if (event.getChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
            this.getActiveAutoChannelsForGuild(event.getGuild()).forEach(vc -> vc.getManager().setParent(event.getChannel().getParent()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
        }
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        this.onMemberJoinAutoChannel(event.getChannelJoined(), event.getMember());
    }

    private void onMemberJoinAutoChannel(@Nonnull VoiceChannel channelJoined, @Nonnull Member member) {
        if (channelJoined.equals(this.getRootAutoChannel(channelJoined.getGuild()))) {
            // if member joins the root channel, create new auto channel
            this.createAutoChannel(member);
        } else if (activeChannels.containsValue(channelJoined.getIdLong()) && !customRenamedChannels.contains(channelJoined.getIdLong())) {
            // the joined channel is an auto channel, let's update it
            channelJoined.getManager().setName(this.createChannelNameFromConnectedMembers(channelJoined)).queue();
        }
    }

    public void createAutoChannel(Member author) {
        VoiceChannel rootChannel = this.getRootAutoChannel(author.getGuild());
        rootChannel.createCopy()
                .setPosition(rootChannel.getPositionRaw())
                .addPermissionOverride(author, CHANNEL_AUTHOR_PERMISSIONS_ALLOW, CHANNEL_AUTHOR_PERMISSIONS_DENY)
                .reason("New auto-channel created by " + author.getUser().getAsTag())
                .queue(vc ->
                {
                    activeChannels.put(vc.getGuild().getIdLong(), vc.getIdLong());
                    channelAuthors.put(vc.getIdLong(), author.getIdLong());
                    vc.getGuild().moveVoiceMember(author, vc).queue();
                });
    }

    @Override
    public void onGuildVoiceMove(@Nonnull GuildVoiceMoveEvent event) {
        VoiceChannel channelLeft = event.getChannelLeft();
        if (activeChannels.containsValue(channelLeft.getIdLong())) {
            this.onMemberLeaveAutoChannel(channelLeft, event.getMember());
        }

        this.onMemberJoinAutoChannel(event.getChannelJoined(), event.getMember());
    }

    @Override
    public void onGuildVoiceLeave(@Nonnull GuildVoiceLeaveEvent event) {
        VoiceChannel channelLeft = event.getChannelLeft();
        if (activeChannels.containsValue(channelLeft.getIdLong())) {
            this.onMemberLeaveAutoChannel(channelLeft, event.getMember());
        }
    }

    @Override
    public void onGenericUserPresence(@Nonnull GenericUserPresenceEvent event) {
        GuildVoiceState state = event.getMember().getVoiceState();
        assert state != null;
        VoiceChannel vc = state.getChannel();
        if (vc != null && activeChannels.containsValue(vc.getIdLong()) && !customRenamedChannels.contains(vc.getIdLong())) {
            // the rate limit for channel name updates was increased to 2 requests every 10 minutes on 05/02/2020
            vc.getManager().setName(this.createChannelNameFromConnectedMembers(vc)).timeout(10, TimeUnit.MINUTES).queue();
        }
    }

    private void onMemberLeaveAutoChannel(VoiceChannel vc, Member member) {
        if (vc.getMembers().isEmpty())  // all members left the channel
        {
            vc.delete().reason("Every member has left the auto-channel").queue(deleted ->
            {
                this.getActiveAutoChannelsForGuild(vc.getGuild())   // fetch other auto channels to update
                        .filter(v -> v.getIdLong() != vc.getIdLong())   // don't update the channel we just deleted
                        .filter(v -> !customRenamedChannels.contains(v.getIdLong()))    // don't update custom renamed channels
                        .forEach(v -> v.getManager().setName(this.createChannelNameFromConnectedMembers(v)).queue());   // update them!
            });
        } else {
            Long currentOwner = channelAuthors.get(vc.getIdLong());
            if (currentOwner != null && currentOwner == member.getIdLong())  // the channel owner left the channel
            {
                // determine new channel owner
                vc.getMembers().stream().findFirst().ifPresent(newOwner ->
                {
                    vc.upsertPermissionOverride(member).reset().queue();   // remove old owner's permissions
                    vc.upsertPermissionOverride(newOwner)
                            .setAllow(CHANNEL_AUTHOR_PERMISSIONS_ALLOW)
                            .setDeny(CHANNEL_AUTHOR_PERMISSIONS_DENY)  // add new owner's permissions
                            .reason("Channel owner " + member.getUser().getAsTag() + " has left their channel, designating " + newOwner.getUser().getAsTag() + " as the new owner")
                            .queue(changed -> channelAuthors.replace(vc.getIdLong(), newOwner.getIdLong()), e -> channelAuthors.remove(vc.getIdLong()));
                });
            }

            if (!customRenamedChannels.contains(vc.getIdLong())) {
                // auto channel has one less member, updating name
                vc.getManager().setName(this.createChannelNameFromConnectedMembers(vc)).queue();
            }
        }
    }
}
