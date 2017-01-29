package me.eddiep.ubot.module;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;

public interface UpdateScheduler extends Module {
    void onPreCheck(UBot uBot);

    Schedule<UpdateType> shouldBuild(UpdateType type, UBot ubot);

    Schedule<UpdateType> shouldPatch(UpdateType type, UBot ubot);

    void patchComplete(UpdateType type, UBot uBot);
}
