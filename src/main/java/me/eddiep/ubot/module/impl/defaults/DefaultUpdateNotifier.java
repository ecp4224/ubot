package me.eddiep.ubot.module.impl.defaults;

import me.eddiep.ubot.UBot;
import me.eddiep.ubot.module.SchedulerModule;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;

public class DefaultUpdateNotifier implements SchedulerModule {
    @Override
    public void init() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public void onPreCheck(UBot uBot) {

    }

    @Override
    public Schedule<UpdateType> shouldBuild(UpdateType type, UBot ubot) {
        return Schedule.now();
    }

    @Override
    public Schedule<UpdateType> shouldPatch(UpdateType type, UBot ubot) {
        return Schedule.now();
    }

    @Override
    public void patchComplete(UpdateType type, UBot uBot) {

    }
}
