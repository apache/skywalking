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
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IServiceNameServiceUIDAO extends DAO {

    /**
     * Returns count of service name which register by entry span.
     *
     * <p>SQL as: select count(SERVICE_NAME) from SERVICE_NAME
     * where SRC_SPAN_TYPE = SpanType.Entry_VALUE
     *
     * @return count of service names
     */
    int getCount();

    /**
     * <p>SQL as: select SERVICE_ID, SERVICE_NAME from SERVICE_NAME
     * where SRC_SPAN_TYPE = SpanType.Entry_VALUE
     * and SERVICE_NAME like '%{keyword}%'
     *
     * <p> Note: keyword might not given
     *
     * @param keyword fuzzy query condition
     * @param topN how many rows should return
     * @return not nullable result list
     */
    List<ServiceInfo> searchService(String keyword, int topN);
}
