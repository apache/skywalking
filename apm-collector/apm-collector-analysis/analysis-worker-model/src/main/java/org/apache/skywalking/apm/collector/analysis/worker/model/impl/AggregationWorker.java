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

package org.apache.skywalking.apm.collector.analysis.worker.model.impl;

import org.apache.skywalking.apm.collector.analysis.worker.model.base.*;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.data.MergeDataCache;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class AggregationWorker<INPUT extends StreamData, OUTPUT extends StreamData> extends AbstractLocalAsyncWorker<INPUT, OUTPUT> {

    private static final Logger logger = LoggerFactory.getLogger(AggregationWorker.class);

    private final MergeDataCache<OUTPUT> mergeDataCache;
    private int messageNum;

    public AggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.mergeDataCache = new MergeDataCache<>();
    }

    @SuppressWarnings("unchecked")
    protected OUTPUT transform(INPUT message) {
        return (OUTPUT)message;
    }

    @Override protected void onWork(INPUT message) throws WorkerException {
        OUTPUT output = transform(message);

        messageNum++;
        aggregate(output);

        if (messageNum >= 1000 || message.getEndOfBatchContext().isEndOfBatch()) {
            sendToNext();
            messageNum = 0;
        }
    }

    private void sendToNext() throws WorkerException {
        mergeDataCache.switchPointer();
        while (mergeDataCache.getLast().isWriting()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new WorkerException(e.getMessage(), e);
            }
        }

        mergeDataCache.getLast().collection().forEach((String id, OUTPUT data) -> {
            if (logger.isDebugEnabled()) {
                logger.debug(data.toString());
            }

            onNext(data);
        });
        mergeDataCache.finishReadingLast();
    }

    private void aggregate(OUTPUT message) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(message.getId())) {
            mergeDataCache.get(message.getId()).mergeAndFormulaCalculateData(message);
        } else {
            message.calculateFormula();
            mergeDataCache.put(message.getId(), message);
        }
        mergeDataCache.finishWriting();
    }
}
