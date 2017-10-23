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
public class TimeSyncService {

    private Logger logger = LogManager.getFormatterLogger(TimeSyncService.class);

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Autowired
    private UrlCreator urlCreator;

    public long allInstance() throws IOException {
        String allInstanceLoadUrl = urlCreator.compound("time/allInstance");
        String allInstanceResponse = HttpClientTools.INSTANCE.get(allInstanceLoadUrl, null);
        logger.debug("load all instance last time data: %s", allInstanceResponse);
        JsonObject allInstanceJson = gson.fromJson(allInstanceResponse, JsonObject.class);
        return allInstanceJson.get("timeBucket").getAsLong();
    }

    public long oneInstance(int instanceId) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("instanceId", String.valueOf(instanceId)));
        String oneInstanceLoadUrl = urlCreator.compound("time/oneInstance");
        String oneInstanceResponse = HttpClientTools.INSTANCE.get(oneInstanceLoadUrl, params);
        logger.debug("load one instance last time data: %s, instance id: %s", oneInstanceResponse, instanceId);
        JsonObject oneInstanceJson = gson.fromJson(oneInstanceResponse, JsonObject.class);
        return oneInstanceJson.get("timeBucket").getAsLong();
    }
}
