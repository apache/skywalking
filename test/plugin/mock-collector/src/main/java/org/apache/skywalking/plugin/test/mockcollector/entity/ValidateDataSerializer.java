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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Created by xin on 2017/7/14.
 */
public class ValidateDataSerializer implements JsonSerializer<ValidateData> {
    @Override
    public JsonElement serialize(ValidateData src, Type typeOfSrc, JsonSerializationContext context) {
        Gson gson = new GsonBuilder().registerTypeAdapter(RegistryItem.class, new RegistryItemSerializer())
            .registerTypeAdapter(SegmentItems.class, new SegmentItemsSerializer()).create();

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("registryItems", gson.toJsonTree(src.getRegistryItem()));
        jsonObject.add("segmentItems", gson.toJsonTree(src.getSegmentItem()));
        return jsonObject;
    }
}
