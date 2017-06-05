package org.skywalking.apm.collector.log;

import org.apache.logging.log4j.Logger;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

/**
 * @author pengys5
 */
public class MockLog {

    public Logger mockito() {
        LogManager logManager = PowerMockito.mock(LogManager.class);
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logManager.getFormatterLogger(Mockito.any())).thenReturn(logger);
        return logger;
    }
}
