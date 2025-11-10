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

package org.apache.skywalking.oap.server.core.alarm.provider.status;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AlarmRunningContext {
    private String ruleId;
    private String expression;
    private String endTime;
    private int additionalPeriod;
    private int size;
    private int silenceCountdown;
    private String entityName;
    private List<WindowValue> windowValues = new ArrayList<>();
    private JsonObject mqeMetricsSnapshot;

    @Data
    public static class Metric {
        private String name;
        private long timeBucket;
        private String value;
    }

    @Data
    public static class WindowValue {
        private int index;
        private List<Metric> metrics = new ArrayList<>();
    }
}
