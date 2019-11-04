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
package org.apache.skywalking.apm.plugin.httpasyncclient.v4.context;

import org.apache.http.protocol.HttpContext;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * @author aderm
 */
public class Transmitter {

    private ContextSnapshot snapshot;
    private HttpContext httpContext;
    private Boolean outExit;
    private AbstractSpan exitSpan;

    public Transmitter(ContextSnapshot snapshot, HttpContext httpContext, Boolean outExit,
        AbstractSpan exitSpan) {
        this.snapshot = snapshot;
        this.httpContext = httpContext;
        this.outExit = outExit;
        this.exitSpan = exitSpan;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public Boolean getOutExit() {
        return outExit;
    }

    public void setOutExit(Boolean outExit) {
        this.outExit = outExit;
    }

    public AbstractSpan getExitSpan() {
        return exitSpan;
    }

    public void setExitSpan(AbstractSpan exitSpan) {
        this.exitSpan = exitSpan;
    }
}
