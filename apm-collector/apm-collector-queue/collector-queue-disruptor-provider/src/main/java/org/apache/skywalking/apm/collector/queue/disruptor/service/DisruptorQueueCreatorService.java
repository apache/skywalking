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


package org.apache.skywalking.apm.collector.queue.disruptor.service;

import org.apache.skywalking.apm.collector.queue.base.QueueEventHandler;
import org.apache.skywalking.apm.collector.queue.base.QueueExecutor;
import org.apache.skywalking.apm.collector.queue.disruptor.base.DisruptorQueueCreator;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;

/**
 * @author peng-yongsheng
 */
public class DisruptorQueueCreatorService implements QueueCreatorService {

    private final DisruptorQueueCreator creator;

    public DisruptorQueueCreatorService() {
        this.creator = new DisruptorQueueCreator();
    }

    @Override public QueueEventHandler create(int queueSize, QueueExecutor executor) {
        return creator.create(queueSize, executor);
    }
}
