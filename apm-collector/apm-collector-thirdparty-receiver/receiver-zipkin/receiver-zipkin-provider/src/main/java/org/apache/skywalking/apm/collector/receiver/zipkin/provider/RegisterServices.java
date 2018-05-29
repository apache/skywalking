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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider;

import org.apache.skywalking.apm.collector.analysis.register.define.service.AgentOsInfo;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;

/**
 * @author wusheng
 */
public class RegisterServices {
    private IApplicationIDService applicationIDService;

    private IInstanceIDService instanceIDService;

    private INetworkAddressIDService networkAddressIDService;

    private IServiceNameService serviceNameService;

    public RegisterServices(
        IApplicationIDService applicationIDService,
        IInstanceIDService instanceIDService,
        INetworkAddressIDService networkAddressIDService,
        IServiceNameService serviceNameService) {
        this.applicationIDService = applicationIDService;
        this.instanceIDService = instanceIDService;
        this.networkAddressIDService = networkAddressIDService;
        this.serviceNameService = serviceNameService;
    }

    public IApplicationIDService getApplicationIDService() {
        return applicationIDService;
    }

    public IInstanceIDService getInstanceIDService() {
        return instanceIDService;
    }

    public INetworkAddressIDService getNetworkAddressIDService() {
        return networkAddressIDService;
    }

    public IServiceNameService getServiceNameService() {
        return serviceNameService;
    }

    /**
     * @param applicationId
     * @param agentUUID in zipkin translation, always means application code. Because no UUID for each process.
     * @return
     */
    public int getOrCreateApplicationInstanceId(int applicationId, String agentUUID) {
        AgentOsInfo agentOsInfo = new AgentOsInfo();
        agentOsInfo.setHostname("N/A");
        agentOsInfo.setOsName("N/A");
        agentOsInfo.setProcessNo(-1);
        return getInstanceIDService().getOrCreateByAgentUUID(applicationId, agentUUID, System.currentTimeMillis(), agentOsInfo);
    }
}
