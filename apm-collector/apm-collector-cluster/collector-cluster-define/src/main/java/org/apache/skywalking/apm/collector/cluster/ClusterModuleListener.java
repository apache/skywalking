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


package org.apache.skywalking.apm.collector.cluster;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peng-yongsheng
 */
public abstract class ClusterModuleListener {

    private Set<String> addresses;

    public ClusterModuleListener() {
        addresses = new HashSet<>();
    }

    public abstract String path();

    public final void addAddress(String address) {
        addresses.add(address);
    }

    public final void removeAddress(String address) {
        addresses.remove(address);
    }

    public final Set<String> getAddresses() {
        return addresses;
    }

    public abstract void serverJoinNotify(String serverAddress);

    public abstract void serverQuitNotify(String serverAddress);
}
