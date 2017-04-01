package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.UrlCreator;
import com.a.eye.skywalking.ui.tools.HttpClientTools;
import com.a.eye.skywalking.ui.tools.TimeTools;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
@Service
public class TopTraceListService {

    private Logger logger = LogManager.getFormatterLogger(TopTraceListService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator urlCreator;

    public String loadWithGlobalTraceId(String globalTraceId, int limit, int from) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("globalTraceId", String.valueOf(globalTraceId)));
        params.add(new BasicNameValuePair("from", String.valueOf(from)));
        params.add(new BasicNameValuePair("limit", String.valueOf(limit)));

        String topSegLoadUrl = urlCreator.compound("/segments/top/globalTraceId");
        String topSegResponse = HttpClientTools.INSTANCE.get(topSegLoadUrl, params);
        logger.debug("load top segment data: %s", topSegResponse);

        return topSegResponse;
    }

    public String loadWithOther(long startTime, long endTime, int minCost, int maxCost, int limit, int from) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));
        params.add(new BasicNameValuePair("from", String.valueOf(from)));
        params.add(new BasicNameValuePair("limit", String.valueOf(limit)));
        params.add(new BasicNameValuePair("minCost", String.valueOf(minCost)));
        params.add(new BasicNameValuePair("maxCost", String.valueOf(maxCost)));

        String topSegLoadUrl = urlCreator.compound("/segments/top/timeSlice");
        String topSegResponse = HttpClientTools.INSTANCE.get(topSegLoadUrl, params);
        logger.debug("load top segment data: %s", topSegResponse);

        return topSegResponse;
    }

    public JsonObject topTraceListDataLoad(long startTime, long endTime, int minCost, int maxCost, int limit, int from, String globalTraceId) throws IOException {
        String topSegResponse = "";
        if (StringUtils.isEmpty(globalTraceId)) {
            topSegResponse = loadWithOther(startTime, endTime, minCost, maxCost, limit, from);
        } else {
            topSegResponse = loadWithGlobalTraceId(globalTraceId, limit, from);
        }

        JsonObject topSegDataJson = new JsonObject();
        JsonArray topSegDataArray = new JsonArray();
        JsonObject topSegJson = gson.fromJson(topSegResponse, JsonObject.class);

        if (topSegJson.has("result")) {
            JsonObject topSegPaging = topSegJson.get("result").getAsJsonObject();
            topSegDataJson = topSegPaging;

            JsonArray dataArray = topSegPaging.get("data").getAsJsonArray();
            for (int i = 0; i < dataArray.size(); i++) {
                JsonObject data = dataArray.get(i).getAsJsonObject();
                long start = data.get("startTime").getAsLong();
                String startStr = TimeTools.dateFormat.format(start);
                String traceIds = data.get("traceIds").getAsString();
                data.addProperty("startTime", startStr);
                data.addProperty("DT_RowId", traceIds);

                if (data.get("isError").getAsBoolean()) {
                    data.addProperty("isError", "Failed");
                } else {
                    data.addProperty("isError", "Success");
                }

                topSegDataArray.add(data);
            }
        }
        topSegDataJson.addProperty("recordsFiltered", topSegDataJson.get("recordsTotal").getAsNumber());
        topSegDataJson.add("data", topSegDataArray);

        return topSegDataJson;
    }
}
