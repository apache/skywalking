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

package org.apache.skywalking.oap.server.core.register.worker;

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.data.EndOfBatchContext;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterDistinctWorker extends AbstractWorker<RegisterSource> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterDistinctWorker.class);

    private final AbstractWorker<RegisterSource> nextWorker;
    private final DataCarrier<RegisterSource> dataCarrier;
    private final Map<RegisterSource, RegisterSource> sources;
    private int messageNum;

    RegisterDistinctWorker(int workerId, AbstractWorker<RegisterSource> nextWorker) {
        super(workerId);
        this.nextWorker = nextWorker;
        this.sources = new HashMap<>();
        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(new AggregatorConsumer(this), 1, 200);
    }

    @Override public final void in(RegisterSource source) {
        source.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(source);
    }

    private void onWork(RegisterSource source) {
        messageNum++;

        if (!sources.containsKey(source)) {
            sources.put(source, source);
        } else {
            sources.get(source).combine(source);
        }

        if (messageNum >= 1000 || source.getEndOfBatchContext().isEndOfBatch()) {
            sources.values().forEach(nextWorker::in);
            sources.clear();
            messageNum = 0;
        }
    }

    private class AggregatorConsumer implements IConsumer<RegisterSource> {

        private final RegisterDistinctWorker aggregator;

        private AggregatorConsumer(RegisterDistinctWorker aggregator) {
            this.aggregator = aggregator;
        }

        @Override public void init() {
        }

        @Override public void consume(List<RegisterSource> sources) {
            Iterator<RegisterSource> sourceIterator = sources.iterator();

            int i = 0;
            while (sourceIterator.hasNext()) {
                RegisterSource source = sourceIterator.next();
                i++;
                if (i == sources.size()) {
                    source.getEndOfBatchContext().setEndOfBatch(true);
                }
                aggregator.onWork(source);
            }
        }

        @Override public void onError(List<RegisterSource> sources, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
