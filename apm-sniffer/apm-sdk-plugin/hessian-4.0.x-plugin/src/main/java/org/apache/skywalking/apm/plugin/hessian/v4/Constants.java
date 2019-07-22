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

package org.apache.skywalking.apm.plugin.hessian.v4;

/**
 * Constant field for hessian plugin
 *
 * @author Alan Lau
 */
public class Constants {

    public static final String ENHANCE_CLASS = "com.caucho.hessian.client.HessianProxy";

    public static final String HESSIAN_SERVICE_EXPORTER_CLASS = "org.springframework.remoting.caucho.HessianServiceExporter";

    public static final String ADDHEADER_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.hessian.v4.HessianAddHeaderInterceptor";

    public static final String HESSIAN_SENDREQUEST_CLASS = "org.apache.skywalking.apm.plugin.hessian.v4.HessianProxySendRequestInterceptor";

    public static final String HESSIAN_SERVLET_ENHANCE_CLASS = "com.caucho.hessian.server.HessianSkeleton";

    public static final String SKELETON_INTERCPTOR_CLASS = "org.apache.skywalking.apm.plugin.hessian.v4.HessianSkeletonInterceptor";

    public static final String CONSTRUCT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.hessian.v4.HessianProxyConstructorInterceptor";

    public static final String SKELETON_CONSTRUCT_INTERCEPTOR = "org.apache.skywalking.apm.plugin.hessian.v4.HessianSkeletonConstructorInterceptor";

    public static final String HESSIAN_SERVICE_EXPORTER_INCERCEPTOR = "org.apache.skywalking.apm.plugin.hessian.v4.HessianServiceExporterInterceptor";
}
