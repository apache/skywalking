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

package org.apache.skywalking.apm.plugin.pulsar;

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;

/**
 * Pulsar {@link org.apache.pulsar.client.impl.SendCallback} enhance required info is required by
 * <code>SendCallback</code> enhanced object method interceptor
 */
public class SendCallbackEnhanceRequiredInfo {

    /**
     * topic name of the producer
     */
    private String topic;

    /**
     * context snapshot
     */
    ContextSnapshot contextSnapshot;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public ContextSnapshot getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(ContextSnapshot contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }
}
