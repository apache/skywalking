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

package org.apache.skywalking.apm.collector.storage.table.register;

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.operator.CoverMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonMergeOperation;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class NetworkAddress extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(NetworkAddressTable.COLUMN_ID, new NonMergeOperation()),
        new Column(NetworkAddressTable.COLUMN_NETWORK_ADDRESS, new NonMergeOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(NetworkAddressTable.COLUMN_ADDRESS_ID, new NonMergeOperation()),
        new Column(NetworkAddressTable.COLUMN_SPAN_LAYER, new CoverMergeOperation()),
        new Column(NetworkAddressTable.COLUMN_SERVER_TYPE, new CoverMergeOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public NetworkAddress() {
        super(STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BYTE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getId();
    }

    @Override public void setMetricId(String metricId) {
        setId(metricId);
    }

    public String getNetworkAddress() {
        return getDataString(1);
    }

    public void setNetworkAddress(String networkAddress) {
        setDataString(1, networkAddress);
    }

    public Integer getAddressId() {
        return getDataInteger(0);
    }

    public void setAddressId(Integer addressId) {
        setDataInteger(0, addressId);
    }

    public Integer getSpanLayer() {
        return getDataInteger(1);
    }

    public void setSpanLayer(Integer spanLayer) {
        setDataInteger(1, spanLayer);
    }

    public Integer getServerType() {
        return getDataInteger(2);
    }

    public void setServerType(Integer serverType) {
        setDataInteger(2, serverType);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new NetworkAddress();
        }
    }
}
