package test.a.eye.cloud.logging;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import org.junit.Test;

public class LoggingTest {

    Logger logger = LogManager.getLogger(LoggingTest.class);

    @Test
    public void testNormalLogging() {
        logger.debug("Hello World");
    }

    @Test
    public void testErrorLogging() {
        logger.error("Hello World", new RuntimeException("Failed message"));
    }

    @Test
    public void testConvertFile() {
        Config.Logging.MAX_LOG_FILE_LENGTH = 2400;
        logger.error("Hello World", new RuntimeException("Failed message"));
    }
}
