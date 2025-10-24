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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler.address;

import org.apache.skywalking.apm.network.ebpf.accesslog.v3.KubernetesProcessAddress;

import java.util.Objects;

public class K8sProcessAddress implements ProcessAddress {
    private final KubernetesProcessAddress address;

    public K8sProcessAddress(KubernetesProcessAddress address) {
        this.address = address;
    }

    @Override
    public String getServiceName() {
        return address.getServiceName();
    }

    @Override
    public String getInstanceName() {
        return address.getPodName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof K8sProcessAddress)) return false;
        K8sProcessAddress that = (K8sProcessAddress) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(address);
    }
}
