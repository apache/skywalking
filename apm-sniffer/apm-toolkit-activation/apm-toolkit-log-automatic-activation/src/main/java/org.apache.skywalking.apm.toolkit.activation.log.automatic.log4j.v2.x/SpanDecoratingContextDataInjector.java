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

package org.apache.skywalking.apm.toolkit.activation.log.automatic.log4j.v2.x;

import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.toolkit.activation.log.automatic.common.Constant;

import java.util.List;

public class SpanDecoratingContextDataInjector implements ContextDataInjector {
    private final ContextDataInjector delegate;

    public SpanDecoratingContextDataInjector(ContextDataInjector delegate) {
        this.delegate = delegate;
    }

    @Override
    public StringMap injectContextData(List<Property> list, StringMap stringMap) {
        StringMap contextData = delegate.injectContextData(list, stringMap);

        if (contextData.containsKey(Constant.TID)) {
            // Assume already instrumented event if traceId is present.
            return contextData;
        }

        if (ContextManager.isActive()) {
            String tid = ContextManager.getGlobalTraceId();
            StringMap newContextData = new SortedArrayStringMap(contextData);
            newContextData.putValue(Constant.TID, tid);
            return newContextData;
        }
        return contextData;

    }

    @Override
    public ReadOnlyStringMap rawContextData() {
        return delegate.rawContextData();
    }
}