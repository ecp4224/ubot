package me.eddiep.ubot.module.impl;

import me.eddiep.ubot.module.UpdateNotifier;
import me.eddiep.ubot.utils.Schedule;
import me.eddiep.ubot.utils.UpdateType;

public class DefaultUpdateNotifier implements UpdateNotifier {
    @Override
    public void init() {

    }

    @Override
    public void deinit() {

    }

    @Override
    public void onPreCheck() {

    }

    @Override
    public Schedule<UpdateType> shouldBuild(UpdateType type) {
        return Schedule.now();
    }

    @Override
    public Schedule<UpdateType> shouldPatch(UpdateType type) {
        return Schedule.now();
    }
}
