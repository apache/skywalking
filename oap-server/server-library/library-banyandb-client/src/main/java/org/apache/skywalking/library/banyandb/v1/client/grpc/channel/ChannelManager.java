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

package org.apache.skywalking.library.banyandb.v1.client.grpc.channel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ConnectivityState;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelManager extends ManagedChannel {
    private final LazyReferenceChannel lazyChannel = new LazyReferenceChannel();

    private static final Set<Status.Code> SC_NETWORK = ImmutableSet.of(
            Status.Code.UNAVAILABLE, Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED, Status.Code.RESOURCE_EXHAUSTED, Status.Code.UNKNOWN
    );

    private final ChannelManagerSettings settings;
    private final ChannelFactory channelFactory;
    private final ScheduledExecutorService executor;
    @VisibleForTesting
    final AtomicReference<Entry> entryRef = new AtomicReference<>();

    public static ChannelManager create(ChannelManagerSettings settings, ChannelFactory channelFactory)
            throws IOException {
        return new ChannelManager(settings, channelFactory, Executors.newSingleThreadScheduledExecutor());
    }

    ChannelManager(ChannelManagerSettings settings, ChannelFactory channelFactory, ScheduledExecutorService executor) throws IOException {
        this.settings = settings;
        this.channelFactory = channelFactory;
        this.executor = executor;

        entryRef.set(new Entry(channelFactory.create()));

        this.executor.scheduleAtFixedRate(
                this::refreshSafely,
                settings.getRefreshInterval(),
                settings.getRefreshInterval(),
                TimeUnit.SECONDS
        );
    }

    private void refreshSafely() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Failed to refresh channels", e);
        }
    }

    void refresh() throws IOException {
        Entry entry = entryRef.get();
        if (!entry.needReconnect) {
            return;
        }
        if (entry.isConnected(entry.reconnectCount.incrementAndGet() > this.settings.getForceReconnectionThreshold())) {
            // Reconnect to the same server is automatically done by GRPC
            // clear the flags
            entry.reset();
            return;
        }
        Entry replacedEntry = entryRef.getAndSet(new Entry(this.channelFactory.create()));
        replacedEntry.shutdown();
    }

    @Override
    public ManagedChannel shutdown() {
        entryRef.get().channel.shutdown();
        if (executor != null) {
            // shutdownNow will cancel scheduled tasks
            executor.shutdownNow();
        }
        return this;
    }

    @Override
    public boolean isShutdown() {
        if (!this.entryRef.get().channel.isShutdown()) {
            return false;
        }
        return executor == null || executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        if (!this.entryRef.get().channel.isTerminated()) {
            return false;
        }
        return executor == null || executor.isTerminated();
    }

    @Override
    public ManagedChannel shutdownNow() {
        entryRef.get().channel.shutdownNow();
        if (executor != null) {
            executor.shutdownNow();
        }
        return this;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
        entryRef.get().channel.awaitTermination(endTimeNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        if (executor != null) {
            long awaitTimeNanos = endTimeNanos - System.nanoTime();
            executor.awaitTermination(awaitTimeNanos, TimeUnit.NANOSECONDS);
        }
        return isTerminated();
    }

    @Override
    public <REQ, RESP> ClientCall<REQ, RESP> newCall(MethodDescriptor<REQ, RESP> methodDescriptor, CallOptions callOptions) {
        return lazyChannel.newCall(methodDescriptor, callOptions);
    }

    @Override
    public String authority() {
        return this.entryRef.get().channel.authority();
    }

    @RequiredArgsConstructor
    static class Entry {
        final ManagedChannel channel;

        final AtomicInteger reconnectCount = new AtomicInteger(0);

        volatile boolean needReconnect = false;

        boolean isConnected(boolean requestConnection) {
            return this.channel.getState(requestConnection) == ConnectivityState.READY;
        }

        void shutdown() {
            this.channel.shutdown();
        }

        void reset() {
            needReconnect = false;
            reconnectCount.set(0);
        }
    }

    private class LazyReferenceChannel extends Channel {
        @Override
        public <REQ, RESP> ClientCall<REQ, RESP> newCall(MethodDescriptor<REQ, RESP> methodDescriptor, CallOptions callOptions) {
            Entry entry = entryRef.get();

            return new NetworkExceptionAwareClientCall<>(entry.channel.newCall(methodDescriptor, callOptions), entry);
        }

        @Override
        public String authority() {
            return ChannelManager.this.authority();
        }
    }

    static class NetworkExceptionAwareClientCall<REQ, RESP> extends ForwardingClientCall.SimpleForwardingClientCall<REQ, RESP> {
        final Entry entry;

        public NetworkExceptionAwareClientCall(ClientCall<REQ, RESP> delegate, Entry entry) {
            super(delegate);
            this.entry = entry;
        }

        @Override
        public void start(Listener<RESP> responseListener, Metadata headers) {
            super.start(
                    new ForwardingClientCallListener.SimpleForwardingClientCallListener<RESP>(responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            if (isNetworkError(status)) {
                                entry.needReconnect = true;
                            }
                            super.onClose(status, trailers);
                        }
                    },
                    headers);
        }
    }

    static boolean isNetworkError(Status status) {
        return SC_NETWORK.contains(status.getCode());
    }
}
