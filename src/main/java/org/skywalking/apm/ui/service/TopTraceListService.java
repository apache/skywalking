/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import org.skywalking.apm.ui.tools.TimeBucketTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author peng-yongsheng
 */
@Service
public class TopTraceListService {

    private Logger logger = LogManager.getFormatterLogger(TopTraceListService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator UrlCreator;

    public String load(long startTime, long endTime, int minCost, int maxCost, int limit,
        int from, String globalTraceId, String operationName, int applicationId, String error,
        String sort) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));
        params.add(new BasicNameValuePair("from", String.valueOf(from)));
        params.add(new BasicNameValuePair("limit", String.valueOf(limit)));
        params.add(new BasicNameValuePair("minCost", String.valueOf(minCost)));
        params.add(new BasicNameValuePair("maxCost", String.valueOf(maxCost)));
        params.add(new BasicNameValuePair("globalTraceId", globalTraceId));
        params.add(new BasicNameValuePair("operationName", operationName));
        params.add(new BasicNameValuePair("applicationId", String.valueOf(applicationId)));
        params.add(new BasicNameValuePair("error", error.toLowerCase()));
        params.add(new BasicNameValuePair("sort", sort));

        String topSegLoadUrl = UrlCreator.compound("segment/top");
        String topSegResponse = HttpClientTools.INSTANCE.get(topSegLoadUrl, params);
        logger.debug("load top segment data: %s", topSegResponse);

        return topSegResponse;
    }

    public JsonObject topTraceListDataLoad(long startTime, long endTime, int minCost, int maxCost, int limit, int from,
        String globalTraceId, String operationName, int applicationId, String error, String sort) throws IOException {
        String topSegResponse = load(startTime, endTime, minCost, maxCost, limit, from, globalTraceId, operationName, applicationId, error, sort);

        JsonObject topSegDataJson = new JsonObject();
        JsonArray topSegDataArray = new JsonArray();
        JsonObject topSegJson = gson.fromJson(topSegResponse, JsonObject.class);

        JsonArray dataArray = topSegJson.get("data").getAsJsonArray();
        for (int i = 0; i < dataArray.size(); i++) {
            JsonObject data = dataArray.get(i).getAsJsonObject();
            long start = data.get("start_time").getAsLong();
            String startStr = TimeBucketTools.format(start);
            String traceIds = data.get("global_trace_id").getAsString();
            data.addProperty("DT_RowId", traceIds);
            data.addProperty("start_time", startStr);

            if (data.get("is_error").getAsBoolean()) {
                data.addProperty("is_error", "Failed");
            } else {
                data.addProperty("is_error", "Success");
            }

            topSegDataArray.add(data);
        }
        topSegDataJson.addProperty("recordsFiltered", topSegJson.get("recordsTotal").getAsNumber());
        topSegDataJson.add("data", topSegDataArray);

        return topSegDataJson;
    }
}
