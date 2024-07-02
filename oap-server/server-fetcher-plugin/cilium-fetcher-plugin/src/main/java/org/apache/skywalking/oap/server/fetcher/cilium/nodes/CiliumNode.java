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

package org.apache.skywalking.oap.server.fetcher.cilium.nodes;

import io.cilium.api.observer.ObserverGrpc;
import io.vavr.Tuple2;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CiliumNode represents a node in the Cilium cluster.
 * Usually it's aware by the Cilium Peer Service.
 */
@RequiredArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode(of = {
    "address"
})
@ToString(of = {
    "address",
    "connected"
})
public class CiliumNode {
    private final String address;
    private final ClientBuilder clientBuilder;

    private volatile ObserverGrpc.ObserverBlockingStub observerStub;
    private volatile boolean closed;
    private final CopyOnWriteArrayList<Closeable> closeables = new CopyOnWriteArrayList<>();

    public ObserverGrpc.ObserverBlockingStub getObserverStub() {
        if (closed) {
            return null;
        }
        if (Objects.nonNull(observerStub)) {
            return observerStub;
        }

        synchronized (this) {
            if (Objects.isNull(observerStub)) {
                final Tuple2<String, Integer> addressTuple = parseAddress();
                observerStub = clientBuilder.buildClient(addressTuple._1, addressTuple._2, ObserverGrpc.ObserverBlockingStub.class);
            }
        }
        return observerStub;
    }

    public void addingCloseable(Closeable closeable) {
        closeables.add(closeable);
    }

    public void close() {
        this.closed = true;
        closeables.forEach(c -> {
            try {
                c.close();
            } catch (Exception e) {
                log.warn("Failed to close the cilium node", e);
            }
        });
    }

    private Tuple2<String, Integer> parseAddress() {
        String[] parts = address.split(":");
        if (parts.length != 2) {
            return new Tuple2<>(address, 4244);
        }
        return new Tuple2<>(parts[0], Integer.parseInt(parts[1]));
    }
}
