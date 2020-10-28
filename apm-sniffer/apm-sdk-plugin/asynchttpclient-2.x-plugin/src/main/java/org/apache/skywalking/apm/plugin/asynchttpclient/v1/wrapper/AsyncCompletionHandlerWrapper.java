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

package org.apache.skywalking.apm.plugin.asynchttpclient.v1.wrapper;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.Response;

public class AsyncCompletionHandlerWrapper extends AsyncCompletionHandler {

    @Override
    public Object onCompleted(Response response) throws Exception {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return response;
    }

    @Override
    public void onThrowable(Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
            ContextManager.stopSpan();
        }
    }
}
