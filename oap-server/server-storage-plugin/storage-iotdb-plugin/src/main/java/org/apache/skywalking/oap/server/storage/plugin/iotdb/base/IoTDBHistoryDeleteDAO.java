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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.joda.time.DateTime;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class IoTDBHistoryDeleteDAO implements IHistoryDeleteDAO {
    private final IoTDBClient client;

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("TTL execution log, model: {}, TTL: {}", model.getName(), ttl);
        }
        long deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmm"));
        client.deleteData(model.getName(), TimeBucket.getTimestamp(deadline));
    }
}
