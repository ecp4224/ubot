package me.eddiep.ubot.module.impl;

import me.eddiep.ubot.module.ErrorNotifier;
import me.eddiep.ubot.module.LogModule;
import org.apache.logging.log4j.Logger;

public class Log4JModule implements LogModule, ErrorNotifier {
    private Logger logger;

    public Log4JModule(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void init() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public void log(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void error(Throwable exception) {
        logger.error("Error in ubot!", exception);
    }
}
