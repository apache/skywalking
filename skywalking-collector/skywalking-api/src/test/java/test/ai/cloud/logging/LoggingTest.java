package test.ai.cloud.logging;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
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
