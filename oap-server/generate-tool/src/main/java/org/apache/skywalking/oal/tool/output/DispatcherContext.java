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

package org.apache.skywalking.oal.tool.output;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oal.tool.parser.AnalysisResult;

@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.PUBLIC)
public class DispatcherContext {
    private List<AnalysisResult> allIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceIndicators = new LinkedList<>();
    private List<AnalysisResult> endpointIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceRelationIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceRelationIndicators = new LinkedList<>();
    private List<AnalysisResult> endpointRelationIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceJVMCPUIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceJVMMemoryIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceJVMMemoryPoolIndicators = new LinkedList<>();
    private List<AnalysisResult> serviceInstanceJVMGCIndicators = new LinkedList<>();
    private List<AnalysisResult> databaseAccessIndicators = new LinkedList<>();
}
