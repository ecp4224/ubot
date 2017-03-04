package me.eddiep.ubot.module;

public interface LogModule extends Module {
    void log(String message);

    void warning(String message);
}
