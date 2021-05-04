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

package org.apache.skywalking.apm.agent.core.naming;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

import java.util.HashMap;
import java.util.Map;

public class EndpointNamingControl implements BootService {
    private static final ILog LOGGER = LogManager.getLogger(EndpointNamingControl.class);
    private Map<OfficialComponent, EndpointNameNamingResolver> resolvers = new HashMap<>();

    public void addResolver(EndpointNameNamingResolver resolver) {
        if (resolver == null) {
            return;
        }
        resolvers.put(resolver.component(), resolver);
    }

    public void addNamingRule(NamingRule namingRule) {

        OfficialComponent component = namingRule.getComponent();
        EndpointNameNamingResolver resolver = resolvers.get(component);
        if (resolver == null) {
            LOGGER.warn("can not find endpoint naming resolver for official component {}, make sure you have this resolver plugin", component.getName());
            return;
        }
        try {
            resolver.addRule(namingRule);
        } catch (Throwable t) {
            LOGGER.error(t, "accept naming rule error {}", namingRule);
        }
    }

    public String resolve(SpanOutline spanOutline) {
        if (spanOutline == null) {
            return null;
        }
        for (EndpointNameNamingResolver resolver : resolvers.values()) {
            String result = null;
            try {
                result = resolver.resolve(spanOutline);
            } catch (Throwable t) {
                LOGGER.error(t, " naming span name '{}' failed for resolver '{}'", spanOutline.getOperationName(), resolver.getClass().getName());
            }
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public void prepare() throws Throwable {
    }

    @Override
    public void boot() throws Throwable {
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
    }
}
