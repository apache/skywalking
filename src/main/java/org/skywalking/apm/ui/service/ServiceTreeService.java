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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author pengys5
 */
@Service
public class ServiceTreeService {

    private Logger logger = LogManager.getFormatterLogger(ServiceTreeService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator UrlCreator;

    public JsonArray load(int entryServiceId, long startTime, long endTime) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("entryServiceId", String.valueOf(entryServiceId)));
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String serviceTreeLoadUrl = UrlCreator.compound("service/tree/entryServiceId");
        String serviceTreeResponse = HttpClientTools.INSTANCE.get(serviceTreeLoadUrl, params);

        logger.debug("load service tree data: %s", serviceTreeResponse);
        return gson.fromJson(serviceTreeResponse, JsonArray.class);
    }

    public JsonArray load(int entryApplicationId, String entryServiceName, long startTime,
        long endTime) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("entryApplicationId", String.valueOf(entryApplicationId)));
        params.add(new BasicNameValuePair("entryServiceName", entryServiceName));
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String serviceTreeLoadUrl = UrlCreator.compound("service/tree/entryServiceName");
        String serviceTreeResponse = HttpClientTools.INSTANCE.get(serviceTreeLoadUrl, params);

        logger.debug("load service tree data: %s", serviceTreeResponse);
        return gson.fromJson(serviceTreeResponse, JsonArray.class);
    }
}
