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
