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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.context;

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * @author zhaoyuguang
 */

public class SWTransmitter {

    private AbstractSpan spanWebflux;
    private AbstractSpan spanGateway;
    private AbstractSpan spanFilter;
    private ContextSnapshot snapshot;
    private String operationName;

    public SWTransmitter(AbstractSpan spanWebflux, ContextSnapshot snapshot, String operationName) {
        this.spanWebflux = spanWebflux;
        this.snapshot = snapshot;
        this.operationName = operationName;
    }

    public AbstractSpan getSpanFilter() {
        return spanFilter;
    }

    public void setSpanFilter(AbstractSpan spanFilter) {
        this.spanFilter = spanFilter;
    }

    public AbstractSpan getSpanWebflux() {
        return spanWebflux;
    }

    public void setSpanWebflux(AbstractSpan spanWebflux) {
        this.spanWebflux = spanWebflux;
    }

    public AbstractSpan getSpanGateway() {
        return spanGateway;
    }

    public void setSpanGateway(AbstractSpan spanGateway) {
        this.spanGateway = spanGateway;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
}
