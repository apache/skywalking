package org.skywalking.apm.collector.ui.jetty.handler.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Calendar;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.TimeSynchronousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class AllInstanceLastTimeGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(AllInstanceLastTimeGetHandler.class);

    @Override public String pathSpec() {
        return "/time/allInstance";
    }

    private TimeSynchronousService service = new TimeSynchronousService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        Long timeBucket = service.allInstanceLastTime();
        logger.debug("all instance last time: {}", timeBucket);

        if (timeBucket == 0) {
            timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis());
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(TimeBucketUtils.INSTANCE.changeTimeBucket2TimeStamp(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket));
        calendar.add(Calendar.SECOND, -5);
        timeBucket = calendar.getTimeInMillis();

        JsonObject timeJson = new JsonObject();
        timeJson.addProperty("timeBucket", TimeBucketUtils.INSTANCE.getSecondTimeBucket(timeBucket));
        return timeJson;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
