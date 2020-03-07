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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.selector.Selector;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * MetricsRemoteWorker forwards the metrics to the target OAP node.
 */
@Slf4j
public class MetricsRemoteWorker extends AbstractWorker<Metrics> {
    private final RemoteSenderService remoteSender;
    private final String remoteReceiverWorkerName;

    MetricsRemoteWorker(ModuleDefineHolder moduleDefineHolder, String remoteReceiverWorkerName) {
        super(moduleDefineHolder);
        this.remoteSender = moduleDefineHolder.find(CoreModule.NAME).provider().getService(RemoteSenderService.class);
        this.remoteReceiverWorkerName = remoteReceiverWorkerName;
    }

    @Override
    public final void in(Metrics metrics) {
        try {
            remoteSender.send(remoteReceiverWorkerName, metrics, Selector.HashCode);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }
}
