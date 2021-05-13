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

package org.apache.skywalking.apm.agent.core.kafka;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;

/**
 * For compatible with {@link ContextManagerExtendService}, don't need to manage connection status by self.
 */
@OverrideImplementor(ContextManagerExtendService.class)
public class KafkaContextManagerExtendService extends ContextManagerExtendService implements KafkaConnectionStatusListener {

    @Override
    public void prepare() {
        ServiceManager.INSTANCE.findService(KafkaProducerManager.class).addListener(this);
    }

    @Override
    public void onStatusChanged(KafkaConnectionStatus status) {
        statusChanged(GRPCChannelStatus.CONNECTED);
    }
}
