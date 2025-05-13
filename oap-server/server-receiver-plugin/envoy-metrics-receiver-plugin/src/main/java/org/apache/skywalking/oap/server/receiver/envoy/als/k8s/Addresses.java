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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import io.envoyproxy.envoy.config.core.v3.Address;

import static java.util.Objects.isNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

public class Addresses {
    public static boolean isValid(final Address address) {
        if (isNull(address)) {
            return false;
        }
        if (address.hasSocketAddress()) {
            return isNotBlank(address.getSocketAddress().getAddress());
        }
        if (address.hasEnvoyInternalAddress()) {
            return isNotBlank(address.getEnvoyInternalAddress().getEndpointId()) &&
                address.getEnvoyInternalAddress().getEndpointId().split(":").length == 2;
        }
        return false;
    }

    public static String getAddressIP(final Address address) {
        if (isNull(address)) {
            return null;
        }
        if (address.hasSocketAddress()) {
            return address.getSocketAddress().getAddress();
        }
        if (address.hasEnvoyInternalAddress()) {
            return address.getEnvoyInternalAddress().getEndpointId().split(":")[0];
        }
        return null;
    }
}
