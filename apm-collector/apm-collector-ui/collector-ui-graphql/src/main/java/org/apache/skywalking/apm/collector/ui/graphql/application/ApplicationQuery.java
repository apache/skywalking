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

package org.apache.skywalking.apm.collector.ui.graphql.application;

import java.util.List;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.graphql.common.Duration;
import org.apache.skywalking.apm.collector.ui.graphql.common.Topology;
import org.apache.skywalking.apm.collector.ui.graphql.server.AppServerInfo;
import org.apache.skywalking.apm.collector.ui.graphql.service.ServiceInfo;

/**
 * @author peng-yongsheng
 */
public class ApplicationQuery implements Query {

    public List<ApplicationNode> getAllApplication(Duration duration) {
        return null;
    }

    public Topology getApplicationTopology(int applicationId, Duration duration) {
        return null;
    }

    public List<ServiceInfo> getSlowService(int applicationId, Duration duration, Integer top) {
        return null;
    }

    public List<AppServerInfo> getServerThroughput(int applicationId, Duration duration, Integer top) {
        return null;
    }
}
