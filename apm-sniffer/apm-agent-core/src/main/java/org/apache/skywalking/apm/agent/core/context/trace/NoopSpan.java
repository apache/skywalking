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


package org.apache.skywalking.apm.agent.core.context.trace;

import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.network.trace.component.Component;

/**
 * The <code>NoopSpan</code> represents a span implementation without any actual operation.
 * This span implementation is for {@link IgnoredTracerContext},
 * for keeping the memory and gc cost as low as possible.
 *
 * @author wusheng
 */
public class NoopSpan implements AbstractSpan {
    public NoopSpan() {
    }

    @Override
    public AbstractSpan log(Throwable t) {
        return this;
    }

    @Override public AbstractSpan errorOccurred() {
        return this;
    }

    public void finish() {

    }

    @Override public AbstractSpan setComponent(Component component) {
        return this;
    }

    @Override public AbstractSpan setComponent(String componentName) {
        return this;
    }

    @Override public AbstractSpan setLayer(SpanLayer layer) {
        return this;
    }

    @Override
    public AbstractSpan tag(String key, String value) {
        return this;
    }

    @Override public AbstractSpan tag(AbstractTag<?> tag, String value) {
        return this;
    }

    @Override public boolean isEntry() {
        return false;
    }

    @Override public boolean isExit() {
        return false;
    }

    @Override public AbstractSpan log(long timestamp, Map<String, ?> event) {
        return this;
    }

    @Override public AbstractSpan setOperationName(String operationName) {
        return this;
    }

    @Override public AbstractSpan start() {
        return this;
    }

    @Override public int getSpanId() {
        return 0;
    }

    @Override public int getOperationId() {
        return 0;
    }

    @Override public String getOperationName() {
        return "";
    }

    @Override public AbstractSpan setOperationId(int operationId) {
        return this;
    }

    @Override public void ref(TraceSegmentRef ref) {
    }

    @Override public AbstractSpan start(long startTime) {
        return this;
    }

    @Override public AbstractSpan setPeer(String remotePeer) {
        return this;
    }

    @Override public AbstractSpan prepareForAsync() {
        return this;
    }

    @Override public AbstractSpan asyncFinish() {
        return this;
    }
}
