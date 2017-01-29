package me.eddiep.ubot.module;

import me.eddiep.ubot.utils.UpdateType;

public interface VersionFetcher extends Module {

    UpdateType fetchVersion();

    void onUpdateScheduled();
}
