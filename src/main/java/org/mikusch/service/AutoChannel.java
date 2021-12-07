package org.mikusch.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateBitrateEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateUserLimitEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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
    public void onChannelDelete(@Nonnull ChannelDeleteEvent event) {
        if (event.isFromType(ChannelType.VOICE)) {
            Guild guild = event.getGuild();
            Channel channel = event.getChannel();
            if (activeChannels.containsValue(channel.getIdLong())) {
                // An auto channel was deleted, remove it from our maps
                activeChannels.remove(guild.getIdLong(), channel.getIdLong());
                channelAuthors.remove(channel.getIdLong());
                customRenamedChannels.remove(channel.getIdLong());
                this.getActiveAutoChannelsForGuild(event.getGuild())
                        .filter(vc -> !customRenamedChannels.contains(vc.getIdLong()))
                        .forEach(vc -> vc.getManager().setName(this.createChannelNameFromConnectedMembers(vc)).queue());
            } else if (channel.equals(this.getRootAutoChannel(event.getGuild()))) {
                // The root channel was deleted, delete all auto channels (this should happen very rarely)
                // This will trigger the other if-branch above
                activeChannels.get(guild.getIdLong()).forEach(id -> event.getJDA().getVoiceChannelById(id).delete().queue());
            }
        }
    }

    @Nonnull
    @CheckReturnValue
    public Stream<AudioChannel> getActiveAutoChannelsForGuild(Guild guild) {
        return activeChannels.get(guild.getIdLong()).stream().map(guild::getVoiceChannelById);
    }

    @Nonnull
    public String createChannelNameFromConnectedMembers(AudioChannel channel) {
        // Map<Activity Name, Count>
        Map<String, Long> activities = channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .flatMap(member -> member.getActivities().stream())
                .filter(activity -> activity.getType() != Activity.ActivityType.CUSTOM_STATUS)
                .collect(Collectors.groupingBy(Activity::getName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        int index = this.getActiveAutoChannelsForGuild(channel.getGuild()).collect(Collectors.toList()).indexOf(channel);
        var sb = new StringBuilder(String.format("#%d ", index + 1));
        if (activities.isEmpty()) {
            // One activity ex. "Team Fortress 2" or no activity ex. "General"
            sb.append("[").append(this.getDefaultChannelName(channel.getGuild()).orElse("General")).append("]");
        } else {
            // Multiple activities ex. "Team Fortress 2, No Man's Sky, Spotify"
            sb.append(activities.keySet().stream().limit(3).collect(Collectors.joining(", ", "[", "]")));
        }

        return StringUtils.abbreviate(sb.toString(), "...]", 100);
    }

    @Nullable
    public VoiceChannel getRootAutoChannel(Guild guild) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT `root_channel_id` FROM `autochannels` WHERE `guild_id` = ?",
                    (rs, rowNum) -> guild.getVoiceChannelById(rs.getLong("root_channel_id")),
                    guild.getIdLong()
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Nonnull
    public Optional<String> getDefaultChannelName(Guild guild) {
        var name = jdbcTemplate.queryForObject(
                "SELECT `default_name` FROM `autochannels` WHERE `guild_id` = ?",
                (rs, rowNum) -> rs.getString("default_name"),
                guild.getIdLong()
        );
        return Optional.ofNullable(name);
    }

    @Override
    public void onChannelUpdateName(@Nonnull ChannelUpdateNameEvent event) {
        if (event.isFromType(ChannelType.VOICE)) {
            if (activeChannels.containsValue(event.getChannel().getIdLong()) && !event.getNewValue().equals(this.createChannelNameFromConnectedMembers((VoiceChannel) event.getChannel()))) {
                // Someone renamed this channel, don't auto-update it anymore
                customRenamedChannels.add(event.getChannel().getIdLong());
            }
        }
    }

    @Override
    public void onChannelUpdateUserLimit(@Nonnull ChannelUpdateUserLimitEvent event) {
        if (event.isFromType(ChannelType.VOICE)) {
            if (event.getChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
                this.getActiveAutoChannelsForGuild(event.getGuild())
                        .map(VoiceChannel.class::cast)
                        .filter(vc -> event.getOldValue() == vc.getUserLimit())
                        .forEach(vc -> vc.getManager().setUserLimit(event.getNewValue()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
            }
        }
    }

    @Override
    public void onChannelUpdateBitrate(@Nonnull ChannelUpdateBitrateEvent event) {
        if (event.isFromType(ChannelType.VOICE)) {
            if (event.getChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
                this.getActiveAutoChannelsForGuild(event.getGuild())
                        .filter(vc -> event.getOldValue() == vc.getBitrate())
                        .forEach(vc -> vc.getManager().setBitrate(event.getNewValue()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
            }
        }
    }

    @Override
    public void onPermissionOverrideUpdate(@Nonnull PermissionOverrideUpdateEvent event) {
        if (event.getChannelType() == ChannelType.VOICE) {
            if (event.getVoiceChannel().equals(this.getRootAutoChannel(event.getGuild()))) {
                this.getActiveAutoChannelsForGuild(event.getGuild())
                        .map(VoiceChannel.class::cast)
                        .forEach(vc -> {
                            vc.getManager()
                                    .sync((VoiceChannel) event.getChannel())
                                    .putPermissionOverride(event.getGuild().getMemberById(channelAuthors.get(vc.getIdLong())), CHANNEL_AUTHOR_PERMISSIONS_ALLOW, CHANNEL_AUTHOR_PERMISSIONS_DENY)
                                    .reason("Synced permissions with root channel")
                                    .queue();
                        });
            }
        }
    }

    @Override
    public void onChannelUpdateParent(@Nonnull ChannelUpdateParentEvent event) {
        if (event.isFromType(ChannelType.VOICE)) {
            VoiceChannel channel = (VoiceChannel) event.getChannel();
            if (channel.equals(this.getRootAutoChannel(event.getGuild()))) {
                this.getActiveAutoChannelsForGuild(event.getGuild())
                        .map(VoiceChannel.class::cast)
                        .forEach(vc -> vc.getManager().setParent(channel.getParentCategory()).reason("Synced property " + event.getPropertyIdentifier() + " with root channel").queue());
            }
        }
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        this.onMemberJoinAutoChannel(event.getChannelJoined(), event.getMember());
    }

    private void onMemberJoinAutoChannel(@Nonnull AudioChannel channelJoined, @Nonnull Member member) {
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
        if (rootChannel != null) {
            rootChannel.createCopy()
                    .setPosition(rootChannel.getPositionRaw())
                    .addPermissionOverride(author, CHANNEL_AUTHOR_PERMISSIONS_ALLOW, CHANNEL_AUTHOR_PERMISSIONS_DENY)
                    .reason("New auto-channel created by " + author.getUser().getAsTag())
                    .queue(vc -> {
                        activeChannels.put(vc.getGuild().getIdLong(), vc.getIdLong());
                        channelAuthors.put(vc.getIdLong(), author.getIdLong());
                        vc.getGuild().moveVoiceMember(author, vc).queue();
                    });
        }
    }

    @Override
    public void onGuildVoiceMove(@Nonnull GuildVoiceMoveEvent event) {
        if (activeChannels.containsValue(event.getChannelLeft().getIdLong())) {
            this.onMemberLeaveAutoChannel((VoiceChannel) event.getChannelLeft(), event.getMember());
        }

        this.onMemberJoinAutoChannel(event.getChannelJoined(), event.getMember());
    }

    @Override
    public void onGuildVoiceLeave(@Nonnull GuildVoiceLeaveEvent event) {
        if (activeChannels.containsValue(event.getChannelLeft().getIdLong())) {
            this.onMemberLeaveAutoChannel((VoiceChannel) event.getChannelLeft(), event.getMember());
        }
    }

    @Override
    public void onGenericUserPresence(@Nonnull GenericUserPresenceEvent event) {
        GuildVoiceState state = event.getMember().getVoiceState();
        AudioChannel channel = state.getChannel();
        if (channel != null && activeChannels.containsValue(channel.getIdLong()) && !customRenamedChannels.contains(channel.getIdLong())) {
            // the rate limit for channel name updates was increased to 2 requests every 10 minutes on 05/02/2020
            channel.getManager().setName(this.createChannelNameFromConnectedMembers(channel)).timeout(10, TimeUnit.MINUTES).queue();
        }
    }

    private void onMemberLeaveAutoChannel(VoiceChannel vc, Member member) {
        // all members left the channel
        if (vc.getMembers().isEmpty()) {
            vc.delete().reason("Every member has left the auto-channel").queue(deleted -> {
                this.getActiveAutoChannelsForGuild(vc.getGuild())   // fetch other auto channels to update
                        .filter(v -> v.getIdLong() != vc.getIdLong())   // don't update the channel we just deleted
                        .filter(v -> !customRenamedChannels.contains(v.getIdLong()))    // don't update custom renamed channels
                        .forEach(v -> v.getManager().setName(this.createChannelNameFromConnectedMembers(v)).queue());   // update them!
            });
        } else {
            // check if the channel owner left the channel
            Long currentOwner = channelAuthors.get(vc.getIdLong());
            if (currentOwner != null && currentOwner == member.getIdLong()) {
                // determine new channel owner
                vc.getMembers().stream().findFirst().ifPresent(newOwner -> {
                    // remove old owner's permissions
                    var override = vc.getPermissionOverride(member);
                    if (override != null) {
                        override.delete().queue();
                    }
                    // add new owner's permissions
                    vc.upsertPermissionOverride(newOwner)
                            .setAllow(CHANNEL_AUTHOR_PERMISSIONS_ALLOW)
                            .setDeny(CHANNEL_AUTHOR_PERMISSIONS_DENY)
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
