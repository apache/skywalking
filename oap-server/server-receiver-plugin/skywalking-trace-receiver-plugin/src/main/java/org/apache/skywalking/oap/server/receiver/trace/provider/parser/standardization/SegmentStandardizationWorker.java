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
import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.buffer.BufferStream;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParse;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentStandardizationWorker extends AbstractWorker<SegmentStandardization> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentStandardizationWorker.class);

    private final BufferStream<UpstreamSegment> stream;

    public SegmentStandardizationWorker(SegmentParse segmentParse, String path,
        int offsetFileMaxSize, int dataFileMaxSize, boolean cleanWhenRestart) throws IOException {
        super(Integer.MAX_VALUE);
        DataCarrier<SegmentStandardization> dataCarrier = new DataCarrier<>(1, 1024);
        dataCarrier.consume(new Consumer(this), 1);

        BufferStream.Builder<UpstreamSegment> builder = new BufferStream.Builder<>(path);
        builder.cleanWhenRestart(cleanWhenRestart);
        builder.dataFileMaxSize(dataFileMaxSize);
        builder.offsetFileMaxSize(offsetFileMaxSize);
        builder.parser(UpstreamSegment.parser());
        builder.callBack(segmentParse);

        stream = builder.build();
        stream.initialize();
    }

    @Override
    public void in(SegmentStandardization standardization) {
        stream.write(standardization.getUpstreamSegment());
    }

    private class Consumer implements IConsumer<SegmentStandardization> {

        private final SegmentStandardizationWorker aggregator;

        private Consumer(SegmentStandardizationWorker aggregator) {
            this.aggregator = aggregator;
        }

        @Override
        public void init() {
        }

        @Override
        public void consume(List<SegmentStandardization> data) {
            Iterator<SegmentStandardization> inputIterator = data.iterator();

            int i = 0;
            while (inputIterator.hasNext()) {
                SegmentStandardization indicator = inputIterator.next();
                i++;
                if (i == data.size()) {
                    indicator.getEndOfBatchContext().setEndOfBatch(true);
                }
                aggregator.in(indicator);
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
