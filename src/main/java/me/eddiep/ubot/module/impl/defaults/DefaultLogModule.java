package me.eddiep.ubot.module.impl.defaults;

import me.eddiep.ubot.module.LogModule;

public class DefaultLogModule implements LogModule {
    public void log(String message) {
        System.out.println("[UBOT] " + message);
    }

    public void warning(String message) {
        System.out.println("[UBOT] !! " + message + " !!");
    }

    @Override
    public void init() {

    }

    @Override
    public void dispose() {

    }
}
