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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x.define;

public interface Constants {
    // NettyRoutingFilter
    String INTERCEPT_CLASS_NETTY_ROUTING_FILTER = "org.springframework.cloud.gateway.filter.NettyRoutingFilter";
    String NETTY_ROUTING_FILTER_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x.NettyRoutingFilterInterceptor";

    // HttpClient
    String INTERCEPT_CLASS_HTTP_CLIENT = "reactor.ipc.netty.http.client.HttpClient";
    String REQUEST_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x.HttpClientRequestInterceptor";

    // HttpClientRequest
    String INTERCEPT_CLASS_HTTP_CLIENT_REQUEST = "reactor.ipc.netty.http.client.HttpClientRequest";
    String HTTPCLIENT_REQUEST_HEADERS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x.HttpclientRequestHeadersInterceptor";
}
