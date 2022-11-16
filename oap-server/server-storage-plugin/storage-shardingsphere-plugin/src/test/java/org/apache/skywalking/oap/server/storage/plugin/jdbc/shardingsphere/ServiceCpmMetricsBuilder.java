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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

public class ServiceCpmMetricsBuilder implements StorageBuilder {
    public ServiceCpmMetricsBuilder() {
    }

    public void entity2Storage(StorageData var1, Convert2Storage var2) {
        ServiceCpmMetrics var3 = (ServiceCpmMetrics) var1;
        var2.accept("entity_id", var3.getEntityId());
        var2.accept("value", new Long(var3.getValue()));
        var2.accept("total", new Long(var3.getTotal()));
        var2.accept("time_bucket", new Long(var3.getTimeBucket()));
    }

    public StorageData storage2Entity(Convert2Entity var1) {
        ServiceCpmMetrics var2 = new ServiceCpmMetrics();
        var2.setEntityId((String) var1.get("entity_id"));
        var2.setValue(((Number) var1.get("value")).longValue());
        var2.setTotal(((Number) var1.get("total")).longValue());
        var2.setTimeBucket(((Number) var1.get("time_bucket")).longValue());
        return var2;
    }
}
