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

package org.apache.skywalking.oap.server.core.query.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EBPFNetworkSamplingRule {
    // The match pattern for HTTP request. This is HTTP URI-oriented.
    // Matches all requests if not set
    private String uriRegex;

    // The minimal request duration to activate the network data(HTTP request/response raw data) sampling.
    // Collecting requests without minimal request duration.
    private Integer minDuration;
    // Collecting requests when the response code is 400-499.
    private boolean when4xx;
    // Collecting requests when the response code is 500-599
    private boolean when5xx;

    // Define how to collect sampled data
    private EBPFNetworkDataCollectingSettings settings;
}
