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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@AllArgsConstructor
@ToString
public class MetricsEntityMetaInfo {

    /**
     * The name of {@link org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic}
     */
    private String serviceName;

    /**
     * The name of {@link org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic}
     */
    private String instanceName;

    /**
     * The name of {@link org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic}
     */
    private String endpointName;

    /**
     * The name of {@link org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic}
     */
    private String processName;

    /**
     * Build Service Entity
     */
    public static MetricsEntityMetaInfo buildService(String serviceName) {
        return new MetricsEntityMetaInfo(serviceName, "", "", "");
    }

    /**
     * Build Service Instance Entity
     */
    public static MetricsEntityMetaInfo buildServiceInstance(String serviceName, String instanceName) {
        return new MetricsEntityMetaInfo(serviceName, instanceName, "", "");
    }

    /**
     * Build Endpoint Entity
     */
    public static MetricsEntityMetaInfo buildEndpoint(String serviceName, String endpointName) {
        return new MetricsEntityMetaInfo(serviceName, "", endpointName, "");
    }

    /**
     * Build Process Entity
     */
    public static MetricsEntityMetaInfo buildProcess(String serviceName, String instanceName, String processName) {
        return new MetricsEntityMetaInfo(serviceName, instanceName, "", processName);
    }

}
