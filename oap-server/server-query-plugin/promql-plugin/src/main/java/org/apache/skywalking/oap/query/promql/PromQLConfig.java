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

package org.apache.skywalking.oap.query.promql;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Setter
@Getter
public class PromQLConfig extends ModuleConfig {
    private String restHost;
    private int restPort;
    private String restContextPath;
    private long restIdleTimeOut = 30000;
    private int restAcceptQueueSize = 0;

    // The following configs are used to build `/api/v1/status/buildinfo` API response.
    private String buildInfoVersion = "2.45.0"; // Declare compatibility with 2.45 LTS version APIs.
    private String buildInfoRevision = "";
    private String buildInfoBranch = "";
    private String buildInfoBuildUser = "";
    private String buildInfoBuildDate = "";
    private String buildInfoGoVersion = "";
}
