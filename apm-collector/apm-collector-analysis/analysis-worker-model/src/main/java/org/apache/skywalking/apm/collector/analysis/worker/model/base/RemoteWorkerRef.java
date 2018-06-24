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

import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class RemoteWorkerRef<INPUT extends RemoteData, OUTPUT extends RemoteData> extends WorkerRef<INPUT, OUTPUT> {

    private static final Logger logger = LoggerFactory.getLogger(RemoteWorkerRef.class);

    private final AbstractRemoteWorker<INPUT, OUTPUT> remoteWorker;
    private final RemoteSenderService remoteSenderService;
    private final int graphId;

    RemoteWorkerRef(AbstractRemoteWorker<INPUT, OUTPUT> remoteWorker, RemoteSenderService remoteSenderService,
        int graphId) {
        super(remoteWorker);
        this.remoteWorker = remoteWorker;
        this.remoteSenderService = remoteSenderService;
        this.graphId = graphId;
    }

    @Override protected void in(INPUT message) {
        try {
            RemoteSenderService.Mode mode = remoteSenderService.send(this.graphId, this.remoteWorker.id(), message, this.remoteWorker.selector());
            if (mode.equals(RemoteSenderService.Mode.Local)) {
                out(message);
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override protected void out(INPUT input) {
        super.out(input);
    }
}
