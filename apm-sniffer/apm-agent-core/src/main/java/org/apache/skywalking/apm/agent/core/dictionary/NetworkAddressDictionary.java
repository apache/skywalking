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


package org.apache.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.network.common.KeyIntValuePair;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.apm.network.register.v2.NetAddressMapping;
import org.apache.skywalking.apm.network.register.v2.NetAddresses;
import org.apache.skywalking.apm.network.register.v2.RegisterGrpc;

import static org.apache.skywalking.apm.agent.core.conf.Config.Dictionary.SERVICE_CODE_BUFFER_SIZE;

/**
 * Map of network address id to network literal address, which is from the collector side.
 *
 * @author wusheng
 */
public enum NetworkAddressDictionary {
    INSTANCE;
    private Map<String, Integer> applicationDictionary = new ConcurrentHashMap<String, Integer>();
    private Set<String> unRegisterServices = new ConcurrentSet<String>();

    public PossibleFound find(String networkAddress) {
        Integer applicationId = applicationDictionary.get(networkAddress);
        if (applicationId != null) {
            return new Found(applicationId);
        } else {
            if (applicationDictionary.size() + unRegisterServices.size() < SERVICE_CODE_BUFFER_SIZE) {
                unRegisterServices.add(networkAddress);
            }
            return new NotFound();
        }
    }

    public void syncRemoteDictionary(
        RegisterGrpc.RegisterBlockingStub networkAddressRegisterServiceBlockingStub) {
        if (unRegisterServices.size() > 0) {
            NetAddressMapping networkAddressMappings = networkAddressRegisterServiceBlockingStub.doNetworkAddressRegister(
                NetAddresses.newBuilder().addAllAddresses(unRegisterServices).build());
            if (networkAddressMappings.getAddressIdsCount() > 0) {
                for (KeyIntValuePair keyWithIntegerValue : networkAddressMappings.getAddressIdsList()) {
                    unRegisterServices.remove(keyWithIntegerValue.getKey());
                    applicationDictionary.put(keyWithIntegerValue.getKey(), keyWithIntegerValue.getValue());
                }
            }
        }
    }
}
