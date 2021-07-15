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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.base;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class IoTDBBatchDAO implements IBatchDAO {
    private final IoTDBClient client;

    public IoTDBBatchDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        try {
            client.write((IoTDBInsertRequest) insertRequest);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void flush(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isEmpty(prepareRequests)) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("batch sql statements execute, data size: {}", prepareRequests.size());
        }
        List<IoTDBInsertRequest> tempPrepareRequests = new ArrayList<>(prepareRequests.size());
        prepareRequests.forEach(prepareRequest -> tempPrepareRequests.add((IoTDBInsertRequest) prepareRequest));
        try {
            client.write(tempPrepareRequests);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
