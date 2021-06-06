package org.mikusch.commands;

import net.dv8tion.jda.api.interactions.commands.Command;

import javax.annotation.Nonnull;

public interface PostCommandAddAction {

    void onCommandAdded(@Nonnull Command command);
}
