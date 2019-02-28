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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.selector.Selector;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterRemoteWorker extends AbstractWorker<RegisterSource> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterRemoteWorker.class);

    private final AbstractWorker<RegisterSource> nextWorker;
    private final RemoteSenderService remoteSender;

    RegisterRemoteWorker(int workerId, ModuleManager moduleManager, AbstractWorker<RegisterSource> nextWorker) {
        super(workerId);
        this.remoteSender = moduleManager.find(CoreModule.NAME).provider().getService(RemoteSenderService.class);
        this.nextWorker = nextWorker;
    }

    @Override public final void in(RegisterSource indicator) {
        try {
            remoteSender.send(nextWorker.getWorkerId(), indicator, Selector.ForeverFirst);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }
}
