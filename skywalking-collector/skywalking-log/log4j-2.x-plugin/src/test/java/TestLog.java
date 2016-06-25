import org.apache.logging.log4j.LogManager;

/**
 * Created by astraea on 2015/12/31.
 */
public class TestLog {
    public static void main(String[] args) {
        org.apache.logging.log4j.Logger logger = LogManager.getLogger(TestLog.class);
        logger.info("{}","Hello World");
    }
}
