/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.compat.BrowserPerfServiceGrpc;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

@RequiredArgsConstructor
public class BrowserPerfServiceHandlerCompat extends BrowserPerfServiceGrpc.BrowserPerfServiceImplBase implements GRPCHandler {
    private final BrowserPerfServiceHandler delegate;

    @Override
    public void collectPerfData(final BrowserPerfData request, final StreamObserver<Commands> responseObserver) {
        delegate.collectPerfData(request, responseObserver);
    }

    @Override
    public StreamObserver<BrowserErrorLog> collectErrorLogs(final StreamObserver<Commands> responseObserver) {
        return delegate.collectErrorLogs(responseObserver);
    }
}
