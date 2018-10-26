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

package org.apache.skywalking.aop.server.receiver.mesh;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.buffer.BufferStream;
import org.apache.skywalking.oap.server.library.buffer.DataStreamReader;

public class MeshDataBufferFileCache implements IConsumer<ServiceMeshMetricDataDecorator>, DataStreamReader.CallBack<ServiceMeshMetric> {
    private MeshModuleConfig config;
    private DataCarrier<ServiceMeshMetricDataDecorator> dataCarrier;
    private BufferStream<ServiceMeshMetric> stream;

    public MeshDataBufferFileCache(MeshModuleConfig config) {
        this.config = config;
        dataCarrier = new DataCarrier<>("MeshDataBufferFileCache", 3, 1024);
    }

    void start() throws IOException {
        dataCarrier.consume(this, 1);
        BufferStream.Builder<ServiceMeshMetric> builder = new BufferStream.Builder<>(config.getBufferPath());
        builder.cleanWhenRestart(config.isBufferFileCleanWhenRestart());
        builder.dataFileMaxSize(config.getBufferDataMaxFileSize());
        builder.offsetFileMaxSize(config.getBufferOffsetMaxFileSize());
        builder.parser(ServiceMeshMetric.parser());
        builder.callBack(this);

        stream = builder.build();
        stream.initialize();
    }

    @Override public void init() {

    }

    public void in(ServiceMeshMetric metric) {
        dataCarrier.produce(new ServiceMeshMetricDataDecorator(metric));
    }

    /**
     * Queue callback, make sure concurrency doesn't happen
     *
     * @param data
     */
    @Override public void consume(List<ServiceMeshMetricDataDecorator> data) {
        for (ServiceMeshMetricDataDecorator decorator : data) {
            if (decorator.tryMetaDataRegister()) {
                TelemetryDataDispatcher.doDispatch(decorator);
            } else {
                stream.write(decorator.getMetric());
            }
        }
    }

    @Override public void onError(List<ServiceMeshMetricDataDecorator> data, Throwable t) {

    }

    @Override public void onExit() {

    }

    /**
     * File buffer callback. Block reading from buffer file, until metadata register done.
     *
     * @param message
     * @return
     */
    @Override public boolean call(ServiceMeshMetric message) {
        ServiceMeshMetricDataDecorator decorator = new ServiceMeshMetricDataDecorator(message);
        if (decorator.tryMetaDataRegister()) {
            TelemetryDataDispatcher.doDispatch(decorator);
            return true;
        }
        return false;
    }
}
