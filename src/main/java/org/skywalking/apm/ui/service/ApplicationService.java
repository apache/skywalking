package org.skywalking.apm.ui.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.creator.UrlCreator;
import org.skywalking.apm.ui.tools.HttpClientTools;
import org.skywalking.apm.ui.tools.TimeBucketTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author pengys5
 */
@Service
public class ApplicationService {
    private Logger logger = LogManager.getFormatterLogger(ApplicationService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator UrlCreator;

    public JsonArray load(String timeBucketType, long startTime, long endTime) throws IOException {
        startTime = TimeBucketTools.buildToSecondTimeBucket(timeBucketType, startTime);
        endTime = TimeBucketTools.buildToSecondTimeBucket(timeBucketType, endTime);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String applicationsLoadUrl = UrlCreator.compound("applications");
        String applicationsResponse = HttpClientTools.INSTANCE.get(applicationsLoadUrl, params);

        logger.debug("load applications data: %s", applicationsResponse);
        return gson.fromJson(applicationsResponse, JsonArray.class);
    }
}
