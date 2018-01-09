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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.skywalking.apm.network.proto.JVMMetric;
import org.apache.skywalking.apm.network.proto.JVMMetrics;
import org.apache.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.apache.skywalking.apm.network.proto.Memory;
import org.apache.skywalking.apm.network.proto.MemoryPool;
import org.apache.skywalking.apm.network.proto.PoolType;

/**
 * @author peng-yongsheng
 */
public class JVMMetricServiceHandlerTestCase {

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub blockingStub = JVMMetricsServiceGrpc.newBlockingStub(channel);

        JVMMetrics.Builder builder = JVMMetrics.newBuilder();
        builder.setApplicationInstanceId(2);

        JVMMetric.Builder metricBuilder = JVMMetric.newBuilder();
        metricBuilder.setTime(System.currentTimeMillis());

        buildMemoryMetric(metricBuilder);
        buildMemoryPoolMetric(metricBuilder);

        builder.addMetrics(metricBuilder.build());

        blockingStub.collect(builder.build());
    }

    private static void buildMemoryPoolMetric(JVMMetric.Builder metricBuilder) {
        MemoryPool.Builder builder = MemoryPool.newBuilder();
        builder.setInit(20);
        builder.setMax(50);
        builder.setCommited(20);
        builder.setUsed(15);
        builder.setType(PoolType.NEWGEN_USAGE);

        metricBuilder.addMemoryPool(builder);
    }

    private static void buildMemoryMetric(JVMMetric.Builder metricBuilder) {
        Memory.Builder builder = Memory.newBuilder();
        builder.setInit(20);
        builder.setMax(50);
        builder.setCommitted(20);
        builder.setUsed(15);
        builder.setIsHeap(true);

        metricBuilder.addMemory(builder);
    }
}
