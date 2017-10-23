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

import org.skywalking.apm.ui.creator.UrlCreator;
import org.skywalking.apm.ui.tools.HttpClientTools;
import org.skywalking.apm.ui.tools.TimeBucketTools;
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
 * @author peng-yongsheng
 */
@Service
public class CostService {

    private Logger logger = LogManager.getFormatterLogger(CostService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator UrlCreator;

    public JsonObject loadCostData(String timeSliceType, long startTime, long endTime) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("timeSliceType", timeSliceType));
        params.add(new BasicNameValuePair("startTime", String.valueOf(startTime)));
        params.add(new BasicNameValuePair("endTime", String.valueOf(endTime)));

        String costLoadUrl = UrlCreator.compound("/nodeRef/resSum/groupTimeSlice");
        String costResponse = HttpClientTools.INSTANCE.get(costLoadUrl, params);
        logger.debug("load cost data: %s", costResponse);

        JsonObject costJson = gson.fromJson(costResponse, JsonObject.class);
        JsonArray costArray = costJson.get("result").getAsJsonArray();

        return buildAxis(costArray, timeSliceType);
    }

    private JsonObject buildAxis(JsonArray costArray, String timeSliceType) {
        JsonObject axisData = new JsonObject();

        JsonArray xAxis = new JsonArray();
        JsonArray s1Axis = new JsonArray();
        JsonArray s3Axis = new JsonArray();
        JsonArray s5Axis = new JsonArray();
        JsonArray slowAxis = new JsonArray();
        JsonArray errorAxis = new JsonArray();
        JsonArray successAxis = new JsonArray();

        for (int i = 0; i < costArray.size(); i++) {
            JsonObject costJson = costArray.get(i).getAsJsonObject();

            xAxis.add(TimeBucketTools.buildXAxis(timeSliceType, costJson.get("timeSlice").getAsString()));
            s1Axis.add(costJson.get("oneSecondLess").getAsDouble());
            s3Axis.add(costJson.get("threeSecondLess").getAsDouble());
            s5Axis.add(costJson.get("fiveSecondLess").getAsDouble());
            slowAxis.add(costJson.get("fiveSecondGreater").getAsDouble());
            errorAxis.add(costJson.get("error").getAsDouble());
            successAxis.add(costJson.get("summary").getAsDouble() - costJson.get("error").getAsDouble());
        }
        axisData.add("xAxis", xAxis);
        axisData.add("s1Axis", s1Axis);
        axisData.add("s3Axis", s3Axis);
        axisData.add("s5Axis", s5Axis);
        axisData.add("slowAxis", slowAxis);
        axisData.add("errorAxis", errorAxis);
        axisData.add("successAxis", successAxis);

        return axisData;
    }
}
