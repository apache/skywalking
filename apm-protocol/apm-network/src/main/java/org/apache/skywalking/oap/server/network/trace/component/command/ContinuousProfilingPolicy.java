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

package org.apache.skywalking.oap.server.network.trace.component.command;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ContinuousProfilingPolicy {
    @SerializedName("ServiceName")
    private String serviceName;
    @SerializedName("UUID")
    private String uuid;
    @SerializedName("Profiling")
    private Map<String, Map<String, Item>> profiling;

    @Data
    public static class Item {
        @SerializedName("Threshold")
        private String threshold;
        @SerializedName("Period")
        private int period;
        @SerializedName("Count")
        private int count;
        @SerializedName("URIList")
        private List<String> uriList;
        @SerializedName("URIRegex")
        private String uriRegex;
    }
}
