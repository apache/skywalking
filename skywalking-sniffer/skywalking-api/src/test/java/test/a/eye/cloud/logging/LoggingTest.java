package test.a.eye.cloud.logging;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.EasyLogger;
import org.junit.Test;

public class LoggingTest {

    EasyLogger easyLogger = LogManager.getLogger(LoggingTest.class);

    @Test
    public void testNormalLogging() {
        easyLogger.debug("Hello World");
    }

    @Test
    public void testErrorLogging() {
        easyLogger.error("Hello World", new RuntimeException("Failed message"));
    }

    @Test
    public void testConvertFile() {
        Config.Logging.MAX_LOG_FILE_LENGTH = 2400;
        easyLogger.error("Hello World", new RuntimeException("Failed message"));
    }
}
