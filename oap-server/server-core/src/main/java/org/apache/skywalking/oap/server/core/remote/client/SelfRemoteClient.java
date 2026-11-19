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

import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.RemoteHandleWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;

@Slf4j
public class SelfRemoteClient implements RemoteClient {

    private final Address address;
    private final RemoteClientMetrics metrics;
    private final IWorkerInstanceGetter workerInstanceGetter;

    public SelfRemoteClient(ModuleDefineHolder moduleDefineHolder, Address address) {
        this.address = address;
        workerInstanceGetter = moduleDefineHolder.find(CoreModule.NAME)
                                                 .provider()
                                                 .getService(IWorkerInstanceGetter.class);
        final MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
                                                                .provider()
                                                                .getService(MetricsCreator.class);
        this.metrics = RemoteClientMetrics.forSelf(metricsCreator, address);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public void connect() {
    }

    @Override
    public void close() {
        throw new UnexpectedException("Self remote client invoked to close.");
    }

    @Override
    public void push(String nextWorkerName, StreamData streamData) {
        final long startNanos = System.nanoTime();
        try {
            final RemoteHandleWorker handleWorker = workerInstanceGetter.get(nextWorkerName);
            if (handleWorker == null) {
                metrics.getErrorCounter().inc();
                log.error("Self remote push discarded: worker {} not found.", nextWorkerName);
                return;
            }
            handleWorker.getWorker().in(streamData);
            metrics.getOutCounter().inc();
            metrics.observeLatencySeconds((System.nanoTime() - startNanos) / 1_000_000_000d);
        } catch (Throwable t) {
            metrics.getErrorCounter().inc();
            log.error(t.getMessage(), t);
        }
    }

    @Override
    public ManagedChannel getChannel() {
        return null;
    }

    @Override
    public int compareTo(RemoteClient o) {
        return address.compareTo(o.getAddress());
    }
}
