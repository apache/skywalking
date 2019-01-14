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
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * @author peng-yongsheng
 */
public class SelfRemoteClient implements RemoteClient {

    private final Address address;
    private CounterMetric remoteOutCounter;

    public SelfRemoteClient(ModuleDefineHolder moduleDefineHolder, Address address) {
        this.address = address;
        remoteOutCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricCreator.class)
            .createCounter("remote_out_count", "The number(client side) of inside remote inside aggregate rpc.",
                new MetricTag.Keys("dest", "self"), new MetricTag.Values(address.toString(), "Y"));
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
