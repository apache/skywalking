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

package org.apache.skywalking.oap.server.core.remote.client;

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.worker.WorkerInstances;

/**
 * @author peng-yongsheng
 */
public class SelfRemoteClient implements RemoteClient {

    private final Address address;

    public SelfRemoteClient(Address address) {
        this.address = address;
    }

    @Override public Address getAddress() {
        return address;
    }

    @Override public void connect() {
    }

    @Override public void close() {
        throw new UnexpectedException("Self remote client invoked to close.");
    }

    @Override public void push(int nextWorkerId, StreamData streamData) {
        WorkerInstances.INSTANCES.get(nextWorkerId).in(streamData);
    }

    @Override public int compareTo(RemoteClient o) {
        return address.compareTo(o.getAddress());
    }
}
