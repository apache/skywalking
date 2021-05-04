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

package org.apache.skywalking.apm.plugin.jetty.v9.server;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.apm.plugin.servlet.ServletRequestInterceptor;
import org.eclipse.jetty.server.HttpChannel;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class HandleInterceptor extends ServletRequestInterceptor {
    @Override
    protected boolean isCollectHTTPParams() {
        return false;
    }

    @Override
    protected int httpParamsLengthThreshold() {
        return 1500;
    }

    @Override
    protected HttpServletRequest obtainServletRequest(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes) {
        HttpChannel httpChannel = (HttpChannel) objInst;
        return httpChannel.getRequest();
    }

    @Override
    protected HttpServletResponse obtainServletResponse(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) {
        HttpChannel httpChannel = (HttpChannel) objInst;
        return httpChannel.getResponse();
    }

    @Override
    protected OfficialComponent getComponentsDefine() {
        return ComponentsDefine.JETTY_SERVER;
    }
}
