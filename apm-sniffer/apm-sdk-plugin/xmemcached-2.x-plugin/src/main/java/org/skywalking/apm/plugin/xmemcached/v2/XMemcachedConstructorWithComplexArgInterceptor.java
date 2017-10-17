/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.xmemcached.v2;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class XMemcachedConstructorWithComplexArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        StringBuilder memcachConnInfo = new StringBuilder();
        @SuppressWarnings("unchecked")
        Map<InetSocketAddress, InetSocketAddress> inetSocketAddressMap = (Map<InetSocketAddress, InetSocketAddress>)allArguments[6];
        StringBuilder master = new StringBuilder();
        for (Entry<InetSocketAddress, InetSocketAddress> entry : inetSocketAddressMap.entrySet()) {
            if (master.length() == 0) {
                master = append(master,entry.getKey());
            }
            memcachConnInfo = append(memcachConnInfo, entry.getValue());
        }
        memcachConnInfo =  master.append(memcachConnInfo);
        Integer l = memcachConnInfo.length();
        if (l > 1) {
            memcachConnInfo = new StringBuilder(memcachConnInfo.substring(0, l - 1));
        }
        objInst.setSkyWalkingDynamicField(memcachConnInfo.toString());
    }

    /**
     * Parse InetSocketAddress
     * @param sb
     * @param inetSocketAddress
     * @return
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
