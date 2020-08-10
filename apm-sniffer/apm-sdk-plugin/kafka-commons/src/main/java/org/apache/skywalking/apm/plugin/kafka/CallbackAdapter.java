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

package org.apache.skywalking.apm.plugin.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * implements Callback and EnhancedInstance, for transformation kafkaTemplate.buildCallback
 */
public class CallbackAdapter implements Callback, EnhancedInstance {

    private Object instance;

    private Callback userCallback;

    public CallbackAdapter(Callback userCallback, Object instance) {
        this.userCallback = userCallback;
        this.instance = instance;
    }

    public CallbackAdapter() {
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        if (userCallback != null) {
            userCallback.onCompletion(metadata, exception);
        }
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return instance;
    }

    @Override
    public void setSkyWalkingDynamicField(Object value) {
        this.instance = value;
    }

    public Callback getUserCallback() {
        return userCallback;
    }
}