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
 * @author peng-yongsheng
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
