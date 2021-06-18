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

package org.apache.skywalking.oap.server.core.config.group;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringFormatGroup;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRule4Openapi;

@Slf4j
public class EndpointNameGrouping {
    @Setter
    private volatile EndpointGroupingRule endpointGroupingRule;
    @Setter
    private volatile EndpointGroupingRule4Openapi endpointGroupingRule4Openapi;

    public String format(String serviceName, String endpointName) {
        String formattedName = endpointName;
        if (endpointGroupingRule4Openapi != null) {
            formattedName = formatByOpenapi(serviceName, formattedName);
        }

        if (endpointGroupingRule != null) {
            formattedName = formatByCustom(serviceName, formattedName);
        }

        return formattedName;
    }

    private String formatByCustom(String serviceName, String endpointName) {
        final StringFormatGroup.FormatResult formatResult = endpointGroupingRule.format(serviceName, endpointName);
        if (log.isDebugEnabled() || log.isTraceEnabled()) {
            if (formatResult.isMatch()) {
                log.debug("Endpoint {} of Service {} has been renamed in group {} by endpointGroupingRule",
                          endpointName, serviceName, formatResult.getName()
                );
            } else {
                log.trace("Endpoint {} of Service {} keeps unchanged.", endpointName, serviceName);
            }
        }
        return formatResult.getName();
    }

    private String formatByOpenapi(String serviceName, String endpointName) {
        final StringFormatGroup.FormatResult formatResult = endpointGroupingRule4Openapi.format(
            serviceName, endpointName);
        if (log.isDebugEnabled() || log.isTraceEnabled()) {
            if (formatResult.isMatch()) {
                log.debug("Endpoint {} of Service {} has been renamed in group {} by endpointGroupingRule4Openapi",
                          endpointName, serviceName, formatResult.getName()
                );
            } else {
                log.trace("Endpoint {} of Service {} keeps unchanged.", endpointName, serviceName);
            }
        }
        return formatResult.getName();
    }
}
