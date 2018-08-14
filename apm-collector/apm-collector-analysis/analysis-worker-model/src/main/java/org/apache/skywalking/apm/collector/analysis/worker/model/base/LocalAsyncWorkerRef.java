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

package org.apache.skywalking.apm.collector.analysis.worker.model.base;

import java.util.*;
import org.apache.skywalking.apm.collector.core.annotations.trace.BatchParameter;
import org.apache.skywalking.apm.collector.core.data.QueueData;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.queue.EndOfBatchContext;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class LocalAsyncWorkerRef<INPUT extends QueueData, OUTPUT extends QueueData> extends WorkerRef<INPUT, OUTPUT> implements IConsumer<INPUT> {

    private static final Logger logger = LoggerFactory.getLogger(LocalAsyncWorkerRef.class);

    private DataCarrier<INPUT> dataCarrier;

    LocalAsyncWorkerRef(NodeProcessor<INPUT, OUTPUT> destinationHandler) {
        super(destinationHandler);
    }

    void setQueueEventHandler(DataCarrier<INPUT> dataCarrier) {
        this.dataCarrier = dataCarrier;
    }

    @Override
    public void consume(@BatchParameter List<INPUT> data) {
        Iterator<INPUT> inputIterator = data.iterator();

        int i = 0;
        while (inputIterator.hasNext()) {
            INPUT input = inputIterator.next();
            i++;
            if (i == data.size()) {
                input.getEndOfBatchContext().setEndOfBatch(true);
            }
            out(input);
        }
    }

    @Override public void init() {
    }

    @Override public void onError(List<INPUT> data, Throwable t) {
        logger.error(t.getMessage(), t);
    }

    @Override public void onExit() {
    }

    @Override protected void in(INPUT input) {
        input.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(input);
    }

    @Override protected void out(INPUT input) {
        super.out(input);
    }
}
