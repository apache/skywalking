/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.dsl.debug.ToJson;

@RequiredArgsConstructor
@Getter
public class PercentileArgument implements ToJson {
    private final BucketedValues bucketedValues;
    private final int[] ranks;

    @Override
    public String toJson() {
        final JsonObject json = JsonParser.parseString(bucketedValues.toJson()).getAsJsonObject();
        final JsonArray rankArray = new JsonArray();
        for (final int rank : ranks) {
            rankArray.add(rank);
        }
        json.add("ranks", rankArray);
        return json.toString();
    }
}
