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

package org.skywalking.apm.collector.agentstream.worker.segment.standardization;

import org.skywalking.apm.collector.agentstream.worker.segment.buffer.SegmentBufferManager;
import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.FlushAndSwitch;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentStandardizationWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(SegmentStandardizationWorker.class);

    public SegmentStandardizationWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
        SegmentBufferManager.INSTANCE.initialize();
    }

    @Override protected void onWork(Object message) throws WorkerException {
        if (message instanceof FlushAndSwitch) {
            SegmentBufferManager.INSTANCE.flush();
        } else if (message instanceof EndOfBatchCommand) {
        } else if (message instanceof UpstreamSegment) {
            UpstreamSegment upstreamSegment = (UpstreamSegment)message;
            SegmentBufferManager.INSTANCE.writeBuffer(upstreamSegment);
        } else {
            logger.error("unhandled message, message instance must UpstreamSegment, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentStandardizationWorker> {
        @Override
        public Role role() {
            return SegmentStandardizationWorker.WorkerRole.INSTANCE;
        }

        @Override
        public SegmentStandardizationWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentStandardizationWorker(role(), clusterContext);
        }

        @Override public int queueSize() {
            return 1024;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentStandardizationWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return null;
        }
    }
}
