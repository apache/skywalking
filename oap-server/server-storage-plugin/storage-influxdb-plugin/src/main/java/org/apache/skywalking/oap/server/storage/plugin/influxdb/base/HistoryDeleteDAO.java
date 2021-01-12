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

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.joda.time.DateTime;

@Slf4j
public class HistoryDeleteDAO implements IHistoryDeleteDAO {
    private final InfluxClient client;

    public HistoryDeleteDAO(InfluxClient client) {
        this.client = client;
    }

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("TTL execution log, model: {}", model.getName());
        }
        try {
            long deadline = Long.parseLong(new DateTime().plusDays(-ttl).toString("yyyyMMddHHmm"));

            client.deleteByQuery(model.getName(), TimeBucket.getTimestamp(deadline));
        } catch (Exception e) {
            log.error("TTL execution log, model: {}, errMsg: {}", model.getName(), e.getMessage());
        }
    }
}
