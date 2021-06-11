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

package org.apache.skywalking.apm.plugin.spring.mvc.commons;

/**
 * Interceptor class name constant variables
 */
public class Constants {
    public static final String GET_BEAN_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.GetBeanInterceptor";

    public static final String INVOKE_FOR_REQUEST_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.InvokeForRequestInterceptor";

    public static final String REQUEST_MAPPING_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RequestMappingMethodInterceptor";

    public static final String REST_MAPPING_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RestMappingMethodInterceptor";

    public static final String REQUEST_KEY_IN_RUNTIME_CONTEXT = "SW_REQUEST";

    public static final String RESPONSE_KEY_IN_RUNTIME_CONTEXT = "SW_RESPONSE";

    public static final String REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT = "SW_REACTIVE_RESPONSE_ASYNC_SPAN";

    public static final String FORWARD_REQUEST_FLAG = "SW_FORWARD_REQUEST_FLAG";

    public static final String WEBFLUX_REQUEST_KEY = "SW_WEBFLUX_REQUEST_KEY";

    public static final String CONTROLLER_METHOD_STACK_DEPTH = "SW_CONTROLLER_METHOD_STACK_DEPTH";
}
