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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class EBPFProfilingTaskExtensionConfig {

    @SerializedName("NetworkSamplings")
    private List<NetworkSamplingRule> networkSamplings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkSamplingRule {
        @SerializedName("URIRegex")
        private String uriRegex;
        @SerializedName("MinDuration")
        private Integer minDuration;
        @SerializedName("When4xx")
        private boolean when4xx;
        @SerializedName("When5xx")
        private boolean when5xx;

        @SerializedName("Settings")
        private CollectSettings settings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectSettings {
        @SerializedName("RequireCompleteRequest")
        private boolean requireCompleteRequest;
        @SerializedName("MaxRequestSize")
        private Integer maxRequestSize;
        @SerializedName("RequireCompleteResponse")
        private boolean requireCompleteResponse;
        @SerializedName("MaxResponseSize")
        private Integer maxResponseSize;
    }
}
