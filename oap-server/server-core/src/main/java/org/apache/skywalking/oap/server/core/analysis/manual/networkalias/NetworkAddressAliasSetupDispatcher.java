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

package org.apache.skywalking.oap.server.core.analysis.manual.networkalias;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;

public class NetworkAddressAliasSetupDispatcher implements SourceDispatcher<NetworkAddressAliasSetup> {
    @Override
    public void dispatch(final NetworkAddressAliasSetup source) {
        final NetworkAddressAlias networkAddressAlias = new NetworkAddressAlias();
        networkAddressAlias.setTimeBucket(source.getTimeBucket());
        networkAddressAlias.setAddress(source.getAddress());
        networkAddressAlias.setRepresentServiceId(source.getRepresentServiceId());
        networkAddressAlias.setRepresentServiceInstanceId(source.getRepresentServiceInstanceId());
        networkAddressAlias.setLastUpdateTimeBucket(source.getTimeBucket());
        MetricsStreamProcessor.getInstance().in(networkAddressAlias);
    }
}
