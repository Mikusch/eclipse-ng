package org.mikusch.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.Optional;

import static net.dv8tion.jda.api.utils.MarkdownUtil.monospace;

@Component
public class UserColorCommandListener extends ListenerAdapter {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserColorCommandListener(JdbcTemplate jdbcTemplate, JDA jda) {
        this.jdbcTemplate = jdbcTemplate;

        jda.addEventListener(this);
        //Only add this command for Banana Land
        var guild = jda.getGuildById(186809082470989824L);
        if (guild != null) {
            guild.upsertCommand(new CommandData("usercolor", "Updates your colored role").addOption(OptionType.STRING, "color", "The color")).queue();
        }
    }

    private static String formatHex(@Nonnull final Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.deferReply().queue(hook -> {
            OptionMapping optionColor = event.getOption("color");
            if (optionColor == null) {
                displayCurrentRoleColor(hook, event.getMember());
            } else {
                var optionColorString = optionColor.getAsString();
                try {
                    var color = Color.decode(optionColorString.startsWith("#") ? optionColorString : "#" + optionColorString);
                    createOrUpdateRole(hook, event.getMember(), color);
                } catch (NumberFormatException e) {
                    hook.editOriginal("Invalid color string: " + MarkdownUtil.monospace(optionColorString)).queue();
                }
            }
        });
    }

    @Override
    public void onUserUpdateName(@Nonnull UserUpdateNameEvent event) {
        jdbcTemplate.query("SELECT `role_id` FROM `usercolors` WHERE `user_id` = ?",
                rs -> {
                    var role = event.getJDA().getRoleById(rs.getLong("role_id"));
                    if (role != null) {
                        role.getManager().setName(event.getNewName()).reason("User changed name").queue();
                    }
                },
                event.getUser().getIdLong()
        );
    }

    /**
     * Deletes a member's usercolor role if they left the guild.
     *
     * @param event the event
     */
    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        var member = event.getMember();
        if (member != null) {
            getRoleForMember(member).ifPresent(role -> role.delete().reason("User left guild").queue());
        }
    }

    /**
     * Deletes the usercolor from the database if the corresponding role was deleted.
     *
     * @param event the event
     */
    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        jdbcTemplate.update("DELETE FROM `usercolors` WHERE `role_id` = ?", event.getRole().getIdLong());
    }

    private void displayCurrentRoleColor(InteractionHook hook, Member member) {
        getRoleForMember(member).ifPresentOrElse(
                role -> {
                    var color = role.getColor() != null ? role.getColor() : Color.BLACK;
                    hook.editOriginal("Your custom role (" + role.getAsMention() + ") has the color " + monospace(formatHex(color)) + ".").queue();
                },
                () -> hook.editOriginal("You do not have a custom role.").queue()
        );
    }

    private void createOrUpdateRole(InteractionHook hook, Member member, Color color) {
        getRoleForMember(member).ifPresentOrElse(
                role -> {
                    // member already had a usercolor at some point
                    if (role != null) {
                        role.getManager().setColor(color).queue(updated -> role.getGuild().addRoleToMember(member, role).queue()); // role exists and needs updating
                        hook.editOriginal("The color of your custom role (" + role.getAsMention() + ") has been successfully updated.").queue();
                    } else {
                        createRole(hook, member, color); // role existed once but is not available anymore
                    }
                },
                () -> createRole(hook, member, color) // member has never had a usercolor role
        );
    }

    private Optional<Role> getRoleForMember(Member member) {
        try {
            var role = jdbcTemplate.queryForObject(
                    "SELECT `role_id` FROM `usercolors` WHERE `guild_id` = ? AND `user_id` = ?",
                    (rs, rowNum) -> member.getGuild().getRoleById(rs.getLong("role_id")),
                    member.getGuild().getIdLong(), member.getIdLong()
            );
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private void createRole(InteractionHook hook, Member member, Color color) {
        var guild = member.getGuild();

        var markerRole = guild.getRoleById(239040969310339073L);
        if (markerRole != null) {
            guild.createRole()
                    .setName(member.getUser().getName())
                    .setColor(color)
                    .setPermissions(Permission.EMPTY_PERMISSIONS)
                    .queue(role -> {
                        jdbcTemplate.update("INSERT INTO `usercolors` (`user_id`, `guild_id`, `role_id`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `role_id` = ?", member.getIdLong(), guild.getIdLong(), role.getIdLong(), role.getIdLong());
                        guild.addRoleToMember(member, role).queue();
                        guild.modifyRolePositions().selectPosition(role).moveTo(markerRole.getPosition() - 1).queue();
                        hook.editOriginal("A new custom role (" + role.getAsMention() + ") with the color " + monospace(formatHex(color)) + " has been successfully created and assigned to you.").queue();
                    });
        }
    }
}
