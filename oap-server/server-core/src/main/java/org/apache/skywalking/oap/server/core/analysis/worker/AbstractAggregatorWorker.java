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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.data.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.worker.annotation.WorkerAnnotationContainer;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractAggregatorWorker<INPUT extends Indicator> extends AbstractWorker<INPUT> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAggregatorWorker.class);

    private AbstractWorker worker;
    private final ModuleManager moduleManager;
    private final DataCarrier<INPUT> dataCarrier;
    private final MergeDataCache<INPUT> mergeDataCache;
    private int messageNum;

    public AbstractAggregatorWorker(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.mergeDataCache = new MergeDataCache<>();
        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(new AggregatorConsumer(this), 1);
    }

    @Override public final void in(INPUT input) {
        input.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(input);
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

    private void onNext(INPUT data) {
        if (worker == null) {
            WorkerAnnotationContainer workerMapper = moduleManager.find(CoreModule.NAME).getService(WorkerAnnotationContainer.class);
            worker = workerMapper.findInstanceByClass(nextWorkerClass());
        }
        worker.in(data);
    }

    public abstract Class nextWorkerClass();

    private void aggregate(INPUT message) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(message)) {
            mergeDataCache.get(message).combine(message);
        } else {
            mergeDataCache.put(message);
        }
        mergeDataCache.finishWriting();
    }

    private class AggregatorConsumer implements IConsumer<INPUT> {

        private final AbstractAggregatorWorker<INPUT> aggregator;

        private AggregatorConsumer(AbstractAggregatorWorker<INPUT> aggregator) {
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
