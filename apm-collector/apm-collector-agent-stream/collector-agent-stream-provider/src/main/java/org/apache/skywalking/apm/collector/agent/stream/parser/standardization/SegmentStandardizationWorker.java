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


package org.apache.skywalking.apm.collector.agent.stream.parser.standardization;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.agent.stream.buffer.SegmentBufferManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentStandardizationWorker extends AbstractLocalAsyncWorker<SegmentStandardization, SegmentStandardization> {

    private final Logger logger = LoggerFactory.getLogger(SegmentStandardizationWorker.class);

    public SegmentStandardizationWorker(ModuleManager moduleManager) {
        super(moduleManager);
        SegmentBufferManager.INSTANCE.initialize(moduleManager);
    }

    @Override public int id() {
        return 108;
    }

    @Override protected void onWork(SegmentStandardization segmentStandardization) throws WorkerException {
        SegmentBufferManager.INSTANCE.writeBuffer(segmentStandardization.getUpstreamSegment());
    }

    public final void flushAndSwitch() {
        SegmentBufferManager.INSTANCE.flush();
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentStandardization, SegmentStandardization, SegmentStandardizationWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<SegmentStandardization> queueCreatorService) {
            super(moduleManager, queueCreatorService);
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
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(standardizationWorker::flushAndSwitch, 10, 3, TimeUnit.SECONDS);
        }
    }
}
