/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.define;

public interface Constants {
    // HttpClientFinalizer
    String INTERCEPT_CLASS_HTTP_CLIENT_FINALIZER = "reactor.netty.http.client.HttpClientFinalizer";
    String CLIENT_FINALIZER_CONSTRUCTOR_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.HttpClientFinalizerConstructorInterceptor";
    String HTTP_CLIENT_FINALIZER_SEND_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.HttpClientFinalizerSendInterceptor";
    String HTTP_CLIENT_FINALIZER_URI_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.HttpClientFinalizerURIInterceptor";
    String HTTP_CLIENT_FINALIZER_RESPONSE_CONNECTION_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.HttpClientFinalizerResponseConnectionInterceptor";

    // NettyRoutingFilter
    String INTERCEPT_CLASS_NETTY_ROUTING_FILTER = "org.springframework.cloud.gateway.filter.NettyRoutingFilter";
    String NETTY_ROUTING_FILTER_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.NettyRoutingFilterInterceptor";

    // TcpClient
    String INTERCEPT_CLASS_TCP_CLIENT = "reactor.netty.tcp.TcpClient";
    String TCP_CLIENT_CONSTRUCTOR_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.TcpClientConstructorInterceptor";

}
