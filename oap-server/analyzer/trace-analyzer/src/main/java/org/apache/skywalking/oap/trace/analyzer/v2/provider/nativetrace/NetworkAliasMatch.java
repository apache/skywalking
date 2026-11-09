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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace;

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;

/**
 * What {@code findNetworkAlias(address)} answers: the stored alias row with its service and instance decoded from
 * their ids when the row is found, as {@code RPCAnalysisListener.parseExit} decodes them, the service first and
 * both always, so a malformed id fails even when the instance is not used. The instance is {@code _blank} when the
 * row names none.
 */
public class NetworkAliasMatch {
    private final String service;
    private final String instance;

    public NetworkAliasMatch(final NetworkAddressAlias alias) {
        final IDManager.ServiceID.ServiceIDDefinition serviceDefinition =
            IDManager.ServiceID.analysisId(alias.getRepresentServiceId());
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceDefinition =
            IDManager.ServiceInstanceID.analysisId(alias.getRepresentServiceInstanceId());
        this.service = serviceDefinition.getName();
        this.instance = instanceDefinition.getName();
    }

    public String getService() {
        return service;
    }

    public String getInstance() {
        return instance;
    }
}
