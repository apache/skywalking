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

package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import java.util.Objects;

/**
 * Extension context, It provides the interaction capabilities between the agents
 * deployed in upstream and downstream services.
 */
public class ExtensionContext {

    /**
     * Tracing Mode. If true means represents all spans generated in this context should skip analysis.
     */
    private boolean skipAnalysis;

    /**
     * Serialize this {@link ExtensionContext} to a {@link String}
     *
     * @return the serialization string.
     */
    String serialize() {
        return skipAnalysis ? "1" : "0";
    }

    /**
     * Deserialize data from {@link String}
     */
    void deserialize(String value) {
        this.skipAnalysis = Objects.equals(value, "1");
    }

    /**
     * Prepare for the cross-process propagation.
     */
    void inject(ContextCarrier carrier) {
        carrier.getExtensionContext().skipAnalysis = this.skipAnalysis;
    }

    /**
     * Extra the {@link ContextCarrier#getExtensionContext()} into this context.
     */
    void extract(ContextCarrier carrier) {
        this.skipAnalysis = carrier.getExtensionContext().skipAnalysis;
    }

    /**
     * Handle the tracing span.
     */
    void handle(AbstractSpan span) {
        if (this.skipAnalysis) {
            span.skipAnalysis();
        }
    }

    /**
     * Clone the context data, work for capture to cross-thread.
     */
    public ExtensionContext clone() {
        final ExtensionContext context = new ExtensionContext();
        context.skipAnalysis = this.skipAnalysis;
        return context;
    }

    void continued(ContextSnapshot snapshot) {
        this.skipAnalysis = snapshot.getExtensionContext().skipAnalysis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionContext that = (ExtensionContext) o;
        return skipAnalysis == that.skipAnalysis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skipAnalysis);
    }
}
