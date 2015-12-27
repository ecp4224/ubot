package me.eddiep.ubot.module;

public interface ErrorNotifier extends Module {
    void error(Throwable exception);
}
