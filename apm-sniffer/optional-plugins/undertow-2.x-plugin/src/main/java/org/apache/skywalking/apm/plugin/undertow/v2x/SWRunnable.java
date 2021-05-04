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

package org.apache.skywalking.apm.plugin.undertow.v2x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class SWRunnable implements Runnable {

    private static final String OPERATION_NAME = "UndertowDispatch";

    private Runnable runnable;

    private ContextSnapshot snapshot;

    public SWRunnable(Runnable runnable, ContextSnapshot snapshot) {
        this.runnable = runnable;
        this.snapshot = snapshot;
    }

    @Override
    public void run() {
        AbstractSpan span = ContextManager.createLocalSpan(SWRunnable.OPERATION_NAME);
        span.setComponent(ComponentsDefine.UNDERTOW);
        try {
            ContextManager.continued(snapshot);
            runnable.run();
        } finally {
            ContextManager.stopSpan();
        }
    }
}
