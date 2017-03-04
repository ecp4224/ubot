package me.eddiep.ubot.module;

import me.eddiep.ubot.utils.UpdateType;

public interface UpdateModule extends Module {

    String getRunningVersion();

    UpdateType checkForUpdates();
}
