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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.standardization;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.buffer.SegmentBufferManager;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentStandardizationWorker extends AbstractLocalAsyncWorker<SegmentStandardization, SegmentStandardization> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentStandardizationWorker.class);

    private SegmentStandardizationWorker(ModuleManager moduleManager) {
        super(moduleManager);
        SegmentBufferManager.INSTANCE.initialize(moduleManager);
    }

    @Override public int id() {
        return WorkerIdDefine.SEGMENT_STANDARDIZATION_WORKER_ID;
    }

    @Override protected void onWork(SegmentStandardization segmentStandardization) throws WorkerException {
        SegmentBufferManager.INSTANCE.writeBuffer(segmentStandardization.getUpstreamSegment());
    }

    public final void flushAndSwitch() {
        SegmentBufferManager.INSTANCE.flush();
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentStandardization, SegmentStandardization, SegmentStandardizationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public SegmentStandardizationWorker workerInstance(ModuleManager moduleManager) {
            SegmentStandardizationWorker standardizationWorker = new SegmentStandardizationWorker(moduleManager);
            startTimer(standardizationWorker);
            return standardizationWorker;
        }

        @Override public int queueSize() {
            return 1024;
        }

        private void startTimer(SegmentStandardizationWorker standardizationWorker) {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(standardizationWorker::flushAndSwitch,
                    t -> logger.error("Segment standardization failure.", t)), 10, 3, TimeUnit.SECONDS);
        }
    }
}
