package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.UrlCreator;
import com.a.eye.skywalking.ui.tools.HttpClientTools;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
@Service
public class SpanService {

    private Logger logger = LogManager.getFormatterLogger(SpanService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator urlCreator;

    public JsonObject loadData(String spanSegId) throws IOException {
        String[] spanSegIds = spanSegId.split("--");
        String segId = spanSegIds[0].replaceAll("A", ".");
        String spanId = spanSegIds[1];

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("segId", segId));
        params.add(new BasicNameValuePair("spanId", spanId));

        String spanLoadUrl = urlCreator.compound("/span/spanId");
        String spanResponse = HttpClientTools.INSTANCE.get(spanLoadUrl, params);
        logger.debug("load span data: %s", spanResponse);

        JsonObject spanResponseJson = gson.fromJson(spanResponse, JsonObject.class);
        JsonObject spanJson = spanResponseJson.get("result").getAsJsonObject();

        JsonArray lo = new JsonArray();
        spanJson.add("lo", lo);

        for (int i = 0; i < 5; i++) {
            JsonObject logs = new JsonObject();
            logs.addProperty("tm", "123123123");

            JsonObject fi = new JsonObject();
            fi.addProperty("sdfsdf1", "sdfsdf");
            fi.addProperty("sdfsdf2", "sdfsdf");
            fi.addProperty("sdfsdf3", "sdfsdf");
            fi.addProperty("sdfsdf4", "sdfsdf");
            fi.addProperty("sdfsdf5", "sdfsdf");
            logs.add("fi", fi);
            lo.add(logs);
        }

        return spanJson;
    }
}
