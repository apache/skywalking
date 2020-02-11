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

package org.apache.skywalking.apm.plugin.xmemcached.v2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithComplexArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        StringBuilder memcachConnInfo = new StringBuilder();
        @SuppressWarnings("unchecked") Map<InetSocketAddress, InetSocketAddress> inetSocketAddressMap = (Map<InetSocketAddress, InetSocketAddress>) allArguments[6];
        for (Entry<InetSocketAddress, InetSocketAddress> entry : inetSocketAddressMap.entrySet()) {
            memcachConnInfo = append(memcachConnInfo, entry.getKey());
            memcachConnInfo = append(memcachConnInfo, entry.getValue());
        }
        Integer length = memcachConnInfo.length();
        if (length > 1) {
            memcachConnInfo = new StringBuilder(memcachConnInfo.substring(0, length - 1));
        }
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }

    /**
     * Parse InetSocketAddress in specified format
     */
    private StringBuilder append(StringBuilder sb, InetSocketAddress inetSocketAddress) {
        if (inetSocketAddress != null) {
            String host = inetSocketAddress.getAddress().getHostAddress();
            int port = inetSocketAddress.getPort();
            sb.append(host).append(":").append(port).append(";");
        }
        return sb;
    }
}
