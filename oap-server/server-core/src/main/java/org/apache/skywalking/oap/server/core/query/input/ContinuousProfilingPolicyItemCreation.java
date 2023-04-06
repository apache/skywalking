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

import lombok.Data;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingMonitorType;

import java.util.List;

@Data
public class ContinuousProfilingPolicyItemCreation {
    // define the monitor type to collect metrics
    private ContinuousProfilingMonitorType type;
    // threshold of policy, which decide by the monitor type
    private String threshold;
    // the length of time to evaluate the metrics
    private int period;
    // how many times after the metrics match the threshold, will trigger profiling
    private int count;
    // the URI path/regex filter when monitor the HTTP related types
    private List<String> uriList;
    private String uriRegex;
}
