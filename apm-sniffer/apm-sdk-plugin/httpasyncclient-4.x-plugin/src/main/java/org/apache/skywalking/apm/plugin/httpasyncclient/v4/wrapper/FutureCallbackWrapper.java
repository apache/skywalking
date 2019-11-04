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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4.wrapper;

import org.apache.http.concurrent.FutureCallback;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL;
import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL_EXIT;
import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL_SPAN;

/**
 * a wrapper for {@link FutureCallback} so we can be notified when the hold response
 * (when one or more request fails the pipeline mode may not callback though we haven't support pipeline)
 * received whether it fails or completed or canceled.
 * @author lican
 */
public class FutureCallbackWrapper<T> implements FutureCallback<T> {

    private FutureCallback<T> callback;

    public FutureCallbackWrapper(FutureCallback<T> callback) {
        this.callback = callback;
    }

    @Override
    public void completed(T o) {
        CONTEXT_LOCAL.remove();
        if (callback != null) {
            callback.completed(o);
        }

        boolean isOutExit = (Boolean) CONTEXT_LOCAL_EXIT.get();
        if (!isOutExit) {
            if (ContextManager.isActive()) {
                ContextManager.stopSpan();
            }
        } else {
            //async finish
            AbstractSpan span = (AbstractSpan) CONTEXT_LOCAL_SPAN.get();
            span.asyncFinish();
        }

        CONTEXT_LOCAL_EXIT.remove();
        CONTEXT_LOCAL_SPAN.remove();
    }

    @Override
    public void failed(Exception e) {
        CONTEXT_LOCAL.remove();

        if (callback != null) {
            callback.failed(e);
        }

        boolean isOutExit = (Boolean) CONTEXT_LOCAL_EXIT.get();
        if (!isOutExit) {
            if (ContextManager.isActive()) {
                ContextManager.activeSpan().errorOccurred().log(e);
                ContextManager.stopSpan();
            }
        } else {
            //async finish
            AbstractSpan span = (AbstractSpan) CONTEXT_LOCAL_SPAN.get();
            span.asyncFinish();
        }

        CONTEXT_LOCAL_EXIT.remove();
        CONTEXT_LOCAL_SPAN.remove();
    }

    @Override
    public void cancelled() {
        CONTEXT_LOCAL.remove();

        if (callback != null) {
            callback.cancelled();
        }

        boolean isOutExit = (Boolean) CONTEXT_LOCAL_EXIT.get();
        if (!isOutExit) {
            if (ContextManager.isActive()) {
                ContextManager.activeSpan().errorOccurred();
                ContextManager.stopSpan();
            }
        } else {
            //async finish
            AbstractSpan span = (AbstractSpan) CONTEXT_LOCAL_SPAN.get();
            span.asyncFinish();
        }

        CONTEXT_LOCAL_EXIT.remove();
        CONTEXT_LOCAL_SPAN.remove();
    }
}
