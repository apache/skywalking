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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EBPFProfilingTaskFixedTimeCreationRequest {
    // Define which processes under the service need to be profiling
    private String serviceId;
    // Aggregate which processes need to be profiling from labels
    private List<String> processLabels;

    // The task start timestamp(ms), if less than or equal zero means the task starts ASAP
    private long startTime;
    // the profiling duration(s)
    private int duration;

    // the task profiling target type
    private EBPFProfilingTargetType targetType;
}