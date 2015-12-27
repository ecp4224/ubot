package me.eddiep.ubot.module.impl;

import me.eddiep.ubot.module.Logger;

public class DefaultLogger implements Logger {
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
    public void deinit() {

    }
}
