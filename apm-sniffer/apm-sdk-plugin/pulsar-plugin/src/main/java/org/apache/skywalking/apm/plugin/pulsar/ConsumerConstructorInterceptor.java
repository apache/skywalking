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

import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * Interceptor of pulsar consumer constructor.
 * <p>
 * The interceptor create {@link ConsumerEnhanceRequiredInfo} which is required by instance method interceptor, So use
 * it to update the skywalking dynamic field of pulsar consumer enhanced instance. So that the instance methods can get
 * the {@link ConsumerEnhanceRequiredInfo}
 */
public class ConsumerConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        PulsarClientImpl pulsarClient = (PulsarClientImpl) allArguments[0];
        String topic = (String) allArguments[1];
        ConsumerConfigurationData consumerConfigurationData = (ConsumerConfigurationData) allArguments[2];
        ConsumerEnhanceRequiredInfo requireInfo = new ConsumerEnhanceRequiredInfo();
        /*
         * Pulsar url can specify with specific URL or a service url provider, use pulsarClient.getLookup().getServiceUrl()
         * can handle the service url provider which use a dynamic service url
         */
        requireInfo.setServiceUrl(pulsarClient.getLookup().getServiceUrl());
        requireInfo.setTopic(topic);
        requireInfo.setSubscriptionName(consumerConfigurationData.getSubscriptionName());
        objInst.setSkyWalkingDynamicField(requireInfo);
    }
}
