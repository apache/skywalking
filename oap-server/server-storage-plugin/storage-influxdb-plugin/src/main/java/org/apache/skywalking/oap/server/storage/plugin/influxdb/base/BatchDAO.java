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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.base;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.BatchPoints;

@Slf4j
public class BatchDAO implements IBatchDAO {
    private final InfluxClient client;

    public BatchDAO(InfluxClient client) {
        this.client = client;
    }

    @Override
    public void asynchronous(InsertRequest insertRequest) {
        client.write(((InfluxInsertRequest) insertRequest).getPoint());
    }

    @Override
    public void synchronous(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isEmpty(prepareRequests)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("batch sql statements execute, data size: {}", prepareRequests.size());
        }

        final BatchPoints.Builder builder = BatchPoints.builder();
        prepareRequests.forEach(e -> {
            builder.point(((InfluxInsertRequest) e).getPoint());
        });

        client.write(builder.build());
    }
}
