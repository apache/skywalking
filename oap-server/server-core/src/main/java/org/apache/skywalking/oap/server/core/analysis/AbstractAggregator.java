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

package org.apache.skywalking.oap.server.core.analysis;

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.data.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractAggregator<INPUT extends StreamData> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAggregator.class);

    private final DataCarrier<INPUT> dataCarrier;
    private final MergeDataCache<INPUT> mergeDataCache;
    private int messageNum;

    public AbstractAggregator() {
        this.mergeDataCache = new MergeDataCache<>();
        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(new AggregatorConsumer(this), 1);
    }

    public void in(INPUT message) {
        message.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(message);
    }

    private void onWork(INPUT message) {
        messageNum++;
        aggregate(message);

        if (messageNum >= 1000 || message.getEndOfBatchContext().isEndOfBatch()) {
            sendToNext();
            messageNum = 0;
        }
    }

    private void sendToNext() {
        mergeDataCache.switchPointer();
        while (mergeDataCache.getLast().isWriting()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        mergeDataCache.getLast().collection().forEach((INPUT key, INPUT data) -> {
            if (logger.isDebugEnabled()) {
                logger.debug(data.toString());
            }

            onNext(data);
        });
        mergeDataCache.finishReadingLast();
    }

    protected abstract void onNext(INPUT data);

    private void aggregate(INPUT message) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(message)) {
//            mergeDataCache.get(message).mergeAndFormulaCalculateData(message);
        } else {
            mergeDataCache.put(message);
        }
        mergeDataCache.finishWriting();
    }

    private class AggregatorConsumer implements IConsumer<INPUT> {

        private final AbstractAggregator<INPUT> aggregator;

        private AggregatorConsumer(AbstractAggregator<INPUT> aggregator) {
            this.aggregator = aggregator;
        }

        @Override public void init() {

        }

        @Override public void consume(List<INPUT> data) {
            Iterator<INPUT> inputIterator = data.iterator();

            int i = 0;
            while (inputIterator.hasNext()) {
                INPUT input = inputIterator.next();
                i++;
                if (i == data.size()) {
                    input.getEndOfBatchContext().setEndOfBatch(true);
                }
                aggregator.onWork(input);
            }
        }

        @Override public void onError(List<INPUT> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
