/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.context.trace;

import java.util.Map;
import org.skywalking.apm.network.trace.component.Component;

public class NoopExitSpan implements AbstractNoopSpan {

    private String peer;
    private int peerId;

    public NoopExitSpan(int peerId) {
        this.peerId = peerId;
    }

    public NoopExitSpan(String peer) {
        this.peer = peer;
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

    @Override public AbstractSpan tag(String key, String value) {
        return this;
    }

    @Override public AbstractSpan log(Throwable t) {
        return this;
    }

    @Override public AbstractSpan errorOccurred() {
        return null;
    }

    @Override public boolean isEntry() {
        return false;
    }

    @Override public boolean isExit() {
        return true;
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

    public int getPeerId() {
        return peerId;
    }

    public String getPeer() {
        return peer;
    }
}
