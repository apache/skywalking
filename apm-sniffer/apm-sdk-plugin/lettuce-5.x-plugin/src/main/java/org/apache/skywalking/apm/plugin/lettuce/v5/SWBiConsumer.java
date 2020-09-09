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

package org.apache.skywalking.apm.plugin.lettuce.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.util.function.BiConsumer;

public class SWBiConsumer<T, U> implements BiConsumer<T, U> {

    private BiConsumer<T, U> biConsumer;
    private ContextSnapshot snapshot;
    private String operationName;

    SWBiConsumer(BiConsumer<T, U> biConsumer, ContextSnapshot snapshot, String operationName) {
        this.biConsumer = biConsumer;
        this.snapshot = snapshot;
        this.operationName = operationName;
    }

    @Override
    public void accept(T t, U u) {
        AbstractSpan span = ContextManager.createLocalSpan(operationName + "/accept");
        span.setComponent(ComponentsDefine.LETTUCE);
        Tags.DB_TYPE.set(span, "Redis");
        SpanLayer.asCache(span);
        try {
            ContextManager.continued(snapshot);
            biConsumer.accept(t, u);
        } catch (Throwable th) {
            ContextManager.activeSpan().log(th);
        } finally {
            ContextManager.stopSpan();
        }
    }
}
