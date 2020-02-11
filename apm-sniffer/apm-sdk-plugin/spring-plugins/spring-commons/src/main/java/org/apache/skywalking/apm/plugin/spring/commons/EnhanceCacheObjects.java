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

package org.apache.skywalking.apm.plugin.spring.commons;

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

public class EnhanceCacheObjects {
    private OfficialComponent component;
    private SpanLayer spanLayer;

    private String operationName;
    private ContextSnapshot contextSnapshot;

    public EnhanceCacheObjects(String operationName, OfficialComponent component, SpanLayer spanLayer,
        ContextSnapshot snapshot) {
        this.component = component;
        this.spanLayer = spanLayer;
        this.operationName = operationName;
        contextSnapshot = snapshot;
    }

    public EnhanceCacheObjects(String operationName, OfficialComponent component, ContextSnapshot snapshot) {
        this(operationName, component, null, snapshot);
    }

    public EnhanceCacheObjects(String operationName, ContextSnapshot snapshot) {
        this(operationName, null, snapshot);
    }

    public OfficialComponent getComponent() {
        return component;
    }

    public SpanLayer getSpanLayer() {
        return spanLayer;
    }

    public String getOperationName() {
        return operationName;
    }

    public ContextSnapshot getContextSnapshot() {
        return contextSnapshot;
    }
}
