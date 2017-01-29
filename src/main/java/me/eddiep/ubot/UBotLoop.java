package me.eddiep.ubot;

import me.eddiep.ubot.utils.CancelToken;

public class UBotLoop implements Runnable {
    private UBot ubot;
    private long interval;
    private CancelToken token;

    UBotLoop(UBot instance, CancelToken token, long interval) {
        this.ubot = instance;
        this.interval = interval;
        this.token = token;
    }

    @Override
    public void run() {
        while (!token.isCanceled()) {
            ubot.check();

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                ubot.getLoggerModule().log("Sleep interrupted! An update check was most likely requested.");
            } catch (Throwable t) {
                ubot.getErrorModule().error(t);
            }
        }
    }
}
