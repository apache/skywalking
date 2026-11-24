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
 */

package org.apache.skywalking.oap.server.admin.trace.pairing.cluster;

import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.apache.skywalking.oap.server.admin.trace.pairing.PairingCapture;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CaptureAck;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CollectPairingsRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CollectPairingsResponse;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.StartCaptureRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.StopCaptureRequest;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.TracePairingDebuggingClusterServiceGrpc;

/**
 * This node's end of the fan-out: a peer that took a REST request asks this node to act on its capture.
 */
public final class PairingClusterServiceImpl
    extends TracePairingDebuggingClusterServiceGrpc.TracePairingDebuggingClusterServiceImplBase {
    private final PairingCapture capture;
    private final String selfNodeId;

    public PairingClusterServiceImpl(final PairingCapture capture, final String selfNodeId) {
        this.capture = capture;
        this.selfNodeId = selfNodeId;
    }

    @Override
    public void startCapture(final StartCaptureRequest request, final StreamObserver<CaptureAck> responseObserver) {
        final long now = System.currentTimeMillis();
        ack(responseObserver, capture.start(now,
            request.getRoundId().isEmpty() ? UUID.randomUUID().toString() : request.getRoundId(),
            request.getStartTime() > 0 ? request.getStartTime() : now));
    }

    @Override
    public void stopCapture(final StopCaptureRequest request, final StreamObserver<CaptureAck> responseObserver) {
        ack(responseObserver, capture.stop());
    }

    @Override
    public void collectPairings(final CollectPairingsRequest request,
                                final StreamObserver<CollectPairingsResponse> responseObserver) {
        capture.read(System.currentTimeMillis());
        responseObserver.onNext(CollectPairingsResponse.newBuilder().setNodeId(selfNodeId)
                                                       .setSummary(capture.summary().toString()).build());
        responseObserver.onCompleted();
    }

    /**
     * @param state the capture's status as the start or stop left it, not read again after
     */
    private void ack(final StreamObserver<CaptureAck> responseObserver, final JsonObject state) {
        responseObserver.onNext(CaptureAck.newBuilder().setNodeId(selfNodeId).setState(state.toString()).build());
        responseObserver.onCompleted();
    }
}
