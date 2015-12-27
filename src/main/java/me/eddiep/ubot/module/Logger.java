package me.eddiep.ubot.module;

public interface Logger extends Module {
    void log(String message);

    void warning(String message);
}
