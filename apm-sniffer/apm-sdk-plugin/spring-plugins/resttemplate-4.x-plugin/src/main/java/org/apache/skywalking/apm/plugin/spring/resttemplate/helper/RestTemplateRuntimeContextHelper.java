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

package org.apache.skywalking.apm.plugin.spring.resttemplate.helper;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;

public class RestTemplateRuntimeContextHelper {

    private static final String REST_TEMPLATE_CONTEXT_CARRIER_KEY_IN_RUNTIME_CONTEXT = "REST_TEMPLATE_CONTEXT_CARRIER";

    private static final String REST_TEMPLATE_URI_KEY_IN_RUNTIME_CONTEXT = "REST_TEMPLATE_URI";

    public static void cleanUri() {
        ContextManager.getRuntimeContext().remove(REST_TEMPLATE_URI_KEY_IN_RUNTIME_CONTEXT);
    }

    public static void cleanContextCarrier() {
        ContextManager.getRuntimeContext().remove(REST_TEMPLATE_CONTEXT_CARRIER_KEY_IN_RUNTIME_CONTEXT);
    }

    public static void addUri(String uri) {
        ContextManager.getRuntimeContext().put(REST_TEMPLATE_URI_KEY_IN_RUNTIME_CONTEXT, uri);
    }

    public static void addContextCarrier(ContextCarrier contextCarrier) {
        ContextManager.getRuntimeContext().put(REST_TEMPLATE_CONTEXT_CARRIER_KEY_IN_RUNTIME_CONTEXT, contextCarrier);
    }

    public static String getUri() {
        return (String) ContextManager.getRuntimeContext().get(REST_TEMPLATE_URI_KEY_IN_RUNTIME_CONTEXT);
    }

    public static ContextCarrier getContextCarrier() {
        return (ContextCarrier) ContextManager.getRuntimeContext().get(REST_TEMPLATE_CONTEXT_CARRIER_KEY_IN_RUNTIME_CONTEXT);
    }
}
