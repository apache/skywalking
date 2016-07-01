package com.ai;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
public class LogbackMain {

    static Logger logger = LoggerFactory.getLogger(LogbackMain.class);

    public static void main(String[] args) throws JoranException {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure("E:\\testdubbo\\GTrace\\GTrace-client\\src\\test\\resources\\logback.xml");
        StatusPrinter.printInCaseOfErrorsOrWarnings(lc);

        logger.info("Hello world11");
    }

}
