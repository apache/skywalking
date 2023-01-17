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

package org.apache.skywalking.oap.meter.analyzer.dsl.registry;

import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;

/**
 * The dynamic entity registry for {@link ProcessTraffic}
 */
public class ProcessRegistry {

    public static final String LOCAL_VIRTUAL_PROCESS = "UNKNOWN_LOCAL";
    public static final String REMOTE_VIRTUAL_PROCESS = "UNKNOWN_REMOTE";

    /**
     * Generate virtual local process under the instance
     * @return the process id
     */
    public static String generateVirtualLocalProcess(String service, String instance) {
        return generateVirtualProcess(service, instance, LOCAL_VIRTUAL_PROCESS);
    }

    /**
     * Generate virtual remote process under the instance
     * trying to generate the name in the kubernetes environment through the remote address
     * @return the process id
     */
    public static String generateVirtualRemoteProcess(String service, String instance, String remoteAddress) {
        // remove port
        String ip = StringUtils.substringBeforeLast(remoteAddress, ":");

        // find remote through k8s metadata
        String name = K8sInfoRegistry.getInstance().findPodByIP(ip);
        if (StringUtils.isEmpty(name)) {
            name = K8sInfoRegistry.getInstance().findServiceByIP(ip);
        }
        // if not exists, then just use remote unknown
        if (StringUtils.isEmpty(name)) {
            name = REMOTE_VIRTUAL_PROCESS;
        }

        return generateVirtualProcess(service, instance, name);
    }

    public static String generateVirtualProcess(String service, String instance, String processName) {
        final ProcessTraffic traffic = new ProcessTraffic();
        final String serviceId = IDManager.ServiceID.buildId(service, true);
        traffic.setServiceId(serviceId);
        traffic.setInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, instance));
        traffic.setName(processName);
        traffic.setAgentId(Const.EMPTY_STRING);
        traffic.setLabelsJson(Const.EMPTY_STRING);
        traffic.setDetectType(ProcessDetectType.VIRTUAL.value());
        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
        traffic.setTimeBucket(timeBucket);
        traffic.setLastPingTimestamp(timeBucket);
        MetricsStreamProcessor.getInstance().in(traffic);
        return traffic.id().build();
    }
}