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

package org.apache.skywalking.apm.collector.ui.graphql.server;

import java.util.List;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.graphql.common.Duration;
import org.apache.skywalking.apm.collector.ui.graphql.common.ResponseTimeTrend;
import org.apache.skywalking.apm.collector.ui.graphql.common.ThroughputTrend;

/**
 * @author peng-yongsheng
 */
public class ServerQuery implements Query {
    public List<AppServerInfo> searchServer(String keyword, Duration duration) {
        return null;
    }

    public List<AppServerInfo> getAllServer(String applicationId, Duration duration) {
        return null;
    }

    public ResponseTimeTrend getServerResponseTimeTrend(int serverId, Duration duration) {
        return null;
    }

    public ThroughputTrend getServerTPSTrend(int serverId, Duration duration) {
        return null;
    }

    public CPUTrend getCPUTrend(int serverId, Duration duration) {
        return null;
    }

    public GCTrend getGCTrend(int serverId, Duration duration) {
        return null;
    }

    public MemoryTrend getMemoryTrend(int serverId, Duration duration) {
        return null;
    }
}
