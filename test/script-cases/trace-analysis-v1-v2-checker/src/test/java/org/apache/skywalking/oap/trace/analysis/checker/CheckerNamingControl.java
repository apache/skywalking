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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.util.function.Supplier;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;

/**
 * The real naming rules, each call recorded with its arguments and its result or exception, in the side's event log.
 */
final class CheckerNamingControl extends NamingControl {
    private final CheckerEventLog log;
    private final EndpointNameGrouping grouping;

    CheckerNamingControl(final int serviceNameMaxLength, final int instanceNameMaxLength,
                           final int endpointNameMaxLength, final EndpointNameGrouping grouping, final CheckerEventLog log) {
        super(serviceNameMaxLength, instanceNameMaxLength, endpointNameMaxLength, grouping);
        this.grouping = grouping;
        this.log = log;
    }

    EndpointNameGrouping grouping() {
        return grouping;
    }

    @Override
    public String formatServiceName(final String serviceName) {
        return record(() -> "formatServiceName(" + quote(serviceName) + ")", () -> super.formatServiceName(serviceName));
    }

    @Override
    public String formatInstanceName(final String instanceName) {
        return record(() -> "formatInstanceName(" + quote(instanceName) + ")", () -> super.formatInstanceName(instanceName));
    }

    @Override
    public String formatEndpointName(final String serviceName, final String endpointName) {
        return record(() -> "formatEndpointName(" + quote(serviceName) + ", " + quote(endpointName) + ")",
            () -> super.formatEndpointName(serviceName, endpointName));
    }

    private String record(final Supplier<String> call, final Supplier<String> real) {
        if (!log.isRecording()) {
            return real.get();
        }
        try {
            final String result = real.get();
            log.event("naming " + call.get() + " -> " + quote(result));
            return result;
        } catch (final RuntimeException e) {
            log.event("naming " + call.get() + " threw " + e.getClass().getName());
            throw e;
        }
    }

    private static String quote(final String text) {
        return text == null ? "null" : CheckerObjectRenderer.quote(text);
    }
}
