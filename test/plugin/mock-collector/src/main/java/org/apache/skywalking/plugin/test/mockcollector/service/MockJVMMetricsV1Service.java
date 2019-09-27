/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.Downstream;
import org.apache.skywalking.apm.network.language.agent.JVMMetrics;
import org.apache.skywalking.apm.network.language.agent.JVMMetricsServiceGrpc;

/**
 * Created by xin on 2017/7/11.
 */
public class MockJVMMetricsV1Service extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase {

    @Override
    public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
