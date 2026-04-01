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

package org.apache.skywalking.oap.server.core.source;

import lombok.Data;

@Data
public class GenAIMetrics {

    private String serviceId;

    private String providerName;

    private String modelName;

    private long inputTokens;

    private long outputTokens;

    /**
     * The total estimated cost of GenAI model calls.
     * The unit is 1*10^-6 of the currency (e.g. micro-USD).
     * * This is stored as a double to maintain precision during calculation and
     * when passing the value to callers (e.g. for Zipkin tags).
     */
    private double totalEstimatedCost;

    private int timeToFirstToken;

    private long latency;

    private boolean status;

    private long timeBucket;
}
