/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.plugin.test.helper;

import org.apache.skywalking.plugin.test.helper.vo.CaseConfiguration;

import java.util.Map;

public interface IConfiguration {
    String agentHome();

    RunningType runningType();

    ScenarioRunningScriptGenerator scenarioGenerator();

    CaseConfiguration caseConfiguration();

    String scenarioName();

    String scenarioVersion();

    String healthCheck();

    String startScript();

    String catalinaOpts();

    String entryService();

    String dockerImageName();

    String dockerContainerName();

    String dockerNetworkName();

    String dockerImageVersion();

    String scenarioHome();

    String outputDir();

    String jacocoHome();

    String debugMode();

    Map<String, Object> toMap();

    String extendEntryHeader();
}
