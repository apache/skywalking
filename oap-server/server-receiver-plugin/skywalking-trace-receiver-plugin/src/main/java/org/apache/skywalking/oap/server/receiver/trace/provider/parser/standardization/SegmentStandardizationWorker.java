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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.buffer.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentStandardizationWorker extends AbstractWorker<SegmentStandardization> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentStandardizationWorker.class);

    private final DataCarrier<SegmentStandardization> dataCarrier;
    private CounterMetrics traceBufferFileIn;

    public SegmentStandardizationWorker(ModuleDefineHolder moduleDefineHolder,
        DataStreamReader.CallBack<UpstreamSegment> segmentParse, String path, int offsetFileMaxSize,
        int dataFileMaxSize, boolean cleanWhenRestart) throws IOException {
        super(moduleDefineHolder);

        BufferStream.Builder<UpstreamSegment> builder = new BufferStream.Builder<>(path);
        builder.cleanWhenRestart(cleanWhenRestart);
        builder.dataFileMaxSize(dataFileMaxSize);
        builder.offsetFileMaxSize(offsetFileMaxSize);
        builder.parser(UpstreamSegment.parser());
        builder.callBack(segmentParse);

        BufferStream<UpstreamSegment> stream = builder.build();
        stream.initialize();

        dataCarrier = new DataCarrier<>("SegmentStandardizationWorker", 1, 1024);
        dataCarrier.consume(new Consumer(stream), 1, 200);

        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        String metricNamePrefix =  "v6_";
        traceBufferFileIn = metricsCreator.createCounter(metricNamePrefix + "trace_buffer_file_in", "The number of trace segment into the buffer file",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
    }

    @Override
    public void in(SegmentStandardization standardization) {
        dataCarrier.produce(standardization);
    }

    private class Consumer implements IConsumer<SegmentStandardization> {

        private final BufferStream<UpstreamSegment> stream;

        private Consumer(BufferStream<UpstreamSegment> stream) {
            this.stream = stream;
        }

        @Override
        public void init() {
        }

        @Override
        public void consume(List<SegmentStandardization> data) {
            for (SegmentStandardization aData : data) {
                traceBufferFileIn.inc();
                stream.write(aData.getUpstreamSegment());
            }
        }

        @Override
        public void onError(List<SegmentStandardization> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {
        }
    }
}
