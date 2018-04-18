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

package org.apache.skywalking.apm.collector.storage.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalApp;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface INetworkAddressUIDAO extends DAO {

    /**
     * <p>SQL as: select count(NETWORK_ADDRESS) from network_address
     * where SRC_SPAN_LAYER = ${srcSpanLayer}
     *
     * @param srcSpanLayer the source layer of this network address register from
     * @return count of network address register from the given source span layer
     */
    int getNumOfSpanLayer(int srcSpanLayer);

    /**
     * Returns the conjectural applications and the application count in every server type.
     *
     * <p>SQL as: select SERVER_TYPE, count(SERVER_TYPE) from network_address
     * where SRC_SPAN_LAYER in (SpanLayer.Database_VALUE, SpanLayer.Cache_VALUE, SpanLayer.MQ_VALUE)
     * group by SERVER_TYPE
     *
     * @return not nullable result list
     */
    List<ConjecturalApp> getConjecturalApps();
}
