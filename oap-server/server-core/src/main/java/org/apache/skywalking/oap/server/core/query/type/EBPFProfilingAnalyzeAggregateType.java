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

package org.apache.skywalking.oap.server.core.query.type;

/**
 * EBPF Profiling analysis data aggregate type
 */
public enum EBPFProfilingAnalyzeAggregateType {
    /**
     * Aggregate by the total duration of stack
     * For "OFF_CPU" target type of profiling: Statics the total time spent in off cpu.
     */
    DURATION,
    /**
     * Aggregate by the trigger count
     * For "ON_CPU" target type of profiling: Statics the number of dump count.
     * For "OFF_CPU" target type of profiling: Statics the number of times the process is switched to off cpu by the scheduler.
     */
    COUNT
}
