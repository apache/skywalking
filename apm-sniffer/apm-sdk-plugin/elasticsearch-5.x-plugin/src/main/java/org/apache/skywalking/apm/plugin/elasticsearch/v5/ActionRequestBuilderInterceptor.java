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

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.elasticsearch.client.transport.TransportClient;

import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ES_ENHANCE_INFO;

/**
 * @author oatiz.
 */
public class ActionRequestBuilderInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        ElasticSearchEnhanceInfo enhanceInfo = new ElasticSearchEnhanceInfo();

        parseClientInfo(allArguments[0], enhanceInfo);

        ContextManager.getRuntimeContext().put(ES_ENHANCE_INFO, enhanceInfo);
    }

    private void parseClientInfo(Object client, ElasticSearchEnhanceInfo enhanceInfo) {

        if (client instanceof TransportClient) {
            TransportClient transportClient = (TransportClient) client;

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < transportClient.transportAddresses().size(); i++) {
                if (i != transportClient.transportAddresses().size() - 1) {
                    builder.append(transportClient.transportAddresses().get(i).toString()).append(",");
                } else {
                    builder.append(transportClient.transportAddresses().get(i).toString());
                }
            }

            enhanceInfo.setTransportAddress(builder.toString());
        } else {
            // avoid NPE
            enhanceInfo.setTransportAddress("");
        }


    }

}
