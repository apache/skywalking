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

package org.apache.skywalking.apm.plugin.armeria;

import com.linecorp.armeria.client.UserClient;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import java.lang.reflect.Method;
import java.net.URI;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

@SuppressWarnings({
    "rawtypes",
    "unused"
})
public class Armeria085ClientInterceptor extends ArmeriaClientInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                             final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

        final UserClient userClient = (UserClient) objInst;
        final URI uri = userClient.uri();
        final HttpMethod httpMethod = (HttpMethod) allArguments[1];
        final String path = (String) allArguments[2];
        final Object req = allArguments[5];

        if (!(req instanceof HttpRequest)) {
            return;
        }

        beforeMethod(uri, httpMethod, path);
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                              final Class<?>[] argumentsTypes, final Object ret) {

        Object req = allArguments[5];

        afterMethod(req);

        return ret;
    }
}
