package org.skywalking.apm.ui.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.creator.UrlCreator;
import org.skywalking.apm.ui.tools.HttpClientTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author pengys5
 */
@Service
public class InstanceMetricService {

    private Logger logger = LogManager.getFormatterLogger(InstanceMetricService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator urlCreator;

    public JsonObject getOsInfo(int instanceId) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("instanceId", String.valueOf(instanceId)));

        String osInfoLoadUrl = urlCreator.compound("instance/os/instanceId");
        String osInfoResponse = HttpClientTools.INSTANCE.get(osInfoLoadUrl, params);

        logger.debug("load os info data: %s", osInfoResponse);
        return gson.fromJson(osInfoResponse, JsonObject.class);
    }

    public JsonObject getMetric(int instanceId, String[] metricTypes, long startTimeBucket,
        long endTimeBucket) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("instanceId", String.valueOf(instanceId)));
        params.add(new BasicNameValuePair("startTimeBucket", String.valueOf(startTimeBucket)));
        params.add(new BasicNameValuePair("endTimeBucket", String.valueOf(endTimeBucket)));

        for (String metricType : metricTypes) {
            params.add(new BasicNameValuePair("metricTypes", metricType));
        }

        String metricLoadUrl = urlCreator.compound("instance/jvm/instanceId/rangeBucket");
        String metricResponse = HttpClientTools.INSTANCE.get(metricLoadUrl, params);

        logger.debug("load instance metric info data: %s", metricResponse);

        return gson.fromJson(metricResponse, JsonObject.class);
    }
}
