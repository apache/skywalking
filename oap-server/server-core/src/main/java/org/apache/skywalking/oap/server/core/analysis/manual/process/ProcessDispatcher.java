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

package org.apache.skywalking.oap.server.core.analysis.manual.process;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.Process;

public class ProcessDispatcher implements SourceDispatcher<Process> {
    @Override
    public void dispatch(Process source) {
        final ProcessTraffic traffic = new ProcessTraffic();
        traffic.setServiceId(source.getServiceId());
        traffic.setInstanceId(source.getInstanceId());
        traffic.setName(source.getName());
        traffic.setLayer(source.getLayer().value());

        traffic.setAgentId(source.getAgentId());
        traffic.setProperties(source.getProperties());
        if (source.getDetectType() != null) {
            traffic.setDetectType(source.getDetectType().value());
        }

        traffic.setTimeBucket(source.getTimeBucket());
        traffic.setLastPingTimestamp(source.getTimeBucket());
        MetricsStreamProcessor.getInstance().in(traffic);
    }
}
