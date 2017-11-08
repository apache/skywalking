/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker.impl;

import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.data.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class AggregationWorker<INPUT extends Data, OUTPUT extends Data> extends AbstractLocalAsyncWorker<INPUT, OUTPUT> {

    private final Logger logger = LoggerFactory.getLogger(AggregationWorker.class);

    private DataCache dataCache;
    private int messageNum;

    public AggregationWorker() {
        dataCache = new DataCache();
    }

    @Override protected final void onWork(INPUT message) throws WorkerException {
        messageNum++;
        aggregate(message);

        if (messageNum >= 100) {
            sendToNext();
            messageNum = 0;
        }
        if (message.isEndOfBatch()) {
            sendToNext();
        }
    }

    private void sendToNext() throws WorkerException {
        dataCache.switchPointer();
        while (dataCache.getLast().isWriting()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new WorkerException(e.getMessage(), e);
            }
        }
        dataCache.getLast().asMap().forEach((String id, Data data) -> {
            logger.debug(data.toString());
            onNext((OUTPUT)data);
        });
        dataCache.finishReadingLast();
    }

    private void aggregate(INPUT message) {
        dataCache.writing();
        if (dataCache.containsKey(message.getId())) {
            message.mergeData(dataCache.get(message.getId()));
        } else {
            dataCache.put(message.getId(), message);
        }
        dataCache.finishWriting();
    }
}
