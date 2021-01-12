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

package org.apache.skywalking.apm.plugin.sjdbc;

import com.dangdang.ddframe.rdb.sharding.executor.event.AbstractExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DMLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DQLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorDataMap;
import com.dangdang.ddframe.rdb.sharding.util.EventBusInstance;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.sjdbc.define.AsyncExecuteInterceptor;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;

/**
 * Sharding-jdbc provides {@link EventBusInstance} to help external systems getDefault events of sql execution. {@link
 * ExecuteEventListener} can getDefault sql statement start and end events, resulting in db span.
 */
public class ExecuteEventListener {

    public static void init() {
        EventBusInstance.getInstance().register(new ExecuteEventListener());
    }

    @Subscribe
    @AllowConcurrentEvents
    public void listenDML(DMLExecutionEvent event) {
        handle(event, "MODIFY");
    }

    @Subscribe
    @AllowConcurrentEvents
    public void listenDQL(DQLExecutionEvent event) {
        handle(event, "QUERY");
    }

    private void handle(AbstractExecutionEvent event, String operation) {
        switch (event.getEventExecutionType()) {
            case BEFORE_EXECUTE:
                AbstractSpan span = ContextManager.createExitSpan("/SJDBC/BRANCH/" + operation, event.getDataSource());
                if (ExecutorDataMap.getDataMap().containsKey(AsyncExecuteInterceptor.SNAPSHOT_DATA_KEY)) {
                    ContextManager.continued((ContextSnapshot) ExecutorDataMap.getDataMap()
                        .get(AsyncExecuteInterceptor.SNAPSHOT_DATA_KEY));
                }
                Tags.DB_TYPE.set(span, "sql");
                Tags.DB_INSTANCE.set(span, event.getDataSource());
                Tags.DB_STATEMENT.set(span, event.getSql());
                if (!event.getParameters().isEmpty()) {
                    String variables = event.getParameters()
                        .stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                    Tags.DB_BIND_VARIABLES.set(span, variables);
                }
                span.setComponent(ComponentsDefine.SHARDING_JDBC);
                SpanLayer.asDB(span);
                break;
            case EXECUTE_FAILURE:
                span = ContextManager.activeSpan();
                span.errorOccurred();
                if (event.getException().isPresent()) {
                    span.log(event.getException().get());
                }
                // fall through
            case EXECUTE_SUCCESS:
                ContextManager.stopSpan();
        }
    }
}
