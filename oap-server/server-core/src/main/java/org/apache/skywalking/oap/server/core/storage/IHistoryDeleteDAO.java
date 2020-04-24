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

package org.apache.skywalking.oap.server.core.storage;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.model.Model;

/**
 * Remove all expired data based on TTL configurations.
 */
public interface IHistoryDeleteDAO extends DAO {
    /**
     * Delete the data
     *
     * @param model                data entity.
     * @param timeBucketColumnName column name represents the time. Right now, always {@link Metrics#TIME_BUCKET}
     * @param ttl                 the number of days should be kept
     * @throws IOException when error happens in the deletion process.
     */
    void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException;
}
