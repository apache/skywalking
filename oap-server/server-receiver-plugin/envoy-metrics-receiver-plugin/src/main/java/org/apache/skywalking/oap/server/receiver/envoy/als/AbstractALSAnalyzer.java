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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;

@Slf4j
public abstract class AbstractALSAnalyzer implements ALSHTTPAnalysis {

    /**
     * Create an adapter to adapt the {@link HTTPAccessLogEntry log entry} into a {@link ServiceMeshMetric.Builder}.
     *
     * @param entry         the access log entry that is to be adapted from.
     * @param sourceService the source service.
     * @param targetService the target/destination service.
     * @return an adapter that adapts {@link HTTPAccessLogEntry log entry} into a {@link ServiceMeshMetric.Builder}.
     */
    protected LogEntry2MetricsAdapter newAdapter(
        final HTTPAccessLogEntry entry,
        final ServiceMetaInfo sourceService,
        final ServiceMetaInfo targetService) {
        return new LogEntry2MetricsAdapter(entry, sourceService, targetService);
    }

}
