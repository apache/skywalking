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

package org.apache.skywalking.oap.server.core.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * NamingControl provides the service to make the names of service, instance and endpoint following the rules or
 * patterns, including length control, grouping, etc.
 */
@RequiredArgsConstructor
@Slf4j
public class NamingControl implements Service {
    private final int serviceNameMaxLength;
    private final int instanceNameMaxLength;
    private final int endpointNameMaxLength;
    private final EndpointNameGrouping endpointNameGrouping;

    /**
     * Format endpoint name by using the length config in the core module. This is a global rule, every place including
     * service as the {@link org.apache.skywalking.oap.server.core.source.Source} should follow this for any core module
     * implementation.
     *
     * @param serviceName raw data, literal string.
     * @return the string, which length less than or equals {@link #serviceNameMaxLength};
     */
    public String formatServiceName(String serviceName) {
        if (serviceName != null && serviceName.length() > serviceNameMaxLength) {
            final String rename = serviceName.substring(0, serviceNameMaxLength);
            if (log.isDebugEnabled()) {
                log.debug(
                    "Service {} has been renamed to {} due to length limitation {}",
                    serviceName,
                    rename,
                    serviceNameMaxLength
                );
            }
            return rename;
        } else {
            return serviceName;
        }
    }

    /**
     * Format endpoint name by using the length config in the core module. This is a global rule, every place including
     * instance as the {@link org.apache.skywalking.oap.server.core.source.Source} should follow this for any core
     * module implementation.
     *
     * @param instanceName raw data, literal string.
     * @return the string, which length less than or equals {@link #instanceNameMaxLength};
     */
    public String formatInstanceName(String instanceName) {
        if (instanceName != null && instanceName.length() > instanceNameMaxLength) {
            final String rename = instanceName.substring(0, instanceNameMaxLength);
            if (log.isDebugEnabled()) {
                log.debug(
                    "Service instance {} has been renamed to {} due to length limitation {}",
                    instanceName,
                    rename,
                    serviceNameMaxLength
                );
            }
            return rename;
        } else {
            return instanceName;
        }
    }

    /**
     * Format endpoint name by using the length config in the core module. This is a global rule, every {@link
     * org.apache.skywalking.oap.server.core.source.Source} including endpoint should follow this for any core module
     * implementation.
     *
     * @param serviceName  the service of the given endpoint.
     * @param endpointName raw data, literal string.
     * @return the string, which length less than or equals {@link #endpointNameMaxLength};
     */
    public String formatEndpointName(String serviceName, String endpointName) {
        if (StringUtil.isEmpty(serviceName) || endpointName == null) {
            return endpointName;
        }

        String lengthControlledName = endpointName;
        if (endpointName.length() > endpointNameMaxLength) {
            lengthControlledName = endpointName.substring(0, endpointNameMaxLength);
            if (log.isDebugEnabled()) {
                log.debug(
                    "Endpoint {} has been renamed to {} due to length limitation {}",
                    endpointName,
                    lengthControlledName,
                    serviceNameMaxLength
                );
            }

        }
        return endpointNameGrouping.format(serviceName, lengthControlledName);
    }
}
