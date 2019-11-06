/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class SegmentItemsSerializer implements JsonSerializer<SegmentItems> {

    @Override
    public JsonElement serialize(SegmentItems src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray applicationSegmentItems = new JsonArray();
        src.getSegmentItems().forEach((applicationCode, segmentItem) -> {
            JsonObject segmentJson = new JsonObject();
            segmentJson.addProperty("applicationCode", applicationCode);
            segmentJson.addProperty("segmentSize", segmentItem.getSegments().size());
            JsonArray segments = new JsonArray();
            segmentItem.getSegments().forEach(segment -> {
                segments.add(new Gson().toJsonTree(segment));
            });
            segmentJson.add("segments", segments);
            applicationSegmentItems.add(segmentJson);
        });

        return applicationSegmentItems;
    }
}
