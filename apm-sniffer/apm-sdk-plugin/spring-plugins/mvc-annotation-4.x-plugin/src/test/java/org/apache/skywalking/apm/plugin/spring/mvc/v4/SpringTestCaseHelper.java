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

package org.apache.skywalking.apm.plugin.spring.mvc.v4;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.JavaxServletRequestHolder;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.JavaxServletResponseHolder;

public final class SpringTestCaseHelper {

    public final static void createCaseHandler(HttpServletRequest request, HttpServletResponse response,
        CaseHandler a) throws Throwable {
        ContextManager.createLocalSpan("For-Test");
        ContextManager.getRuntimeContext().put(Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT, new JavaxServletRequestHolder(request));
        ContextManager.getRuntimeContext().put(Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT, new JavaxServletResponseHolder(response));
        a.handleCase();
        ContextManager.stopSpan();
        ContextManager.getRuntimeContext().remove(Constants.CONTROLLER_METHOD_STACK_DEPTH);
    }

    public interface CaseHandler {
        void handleCase() throws Throwable;
    }
}
