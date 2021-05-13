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
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

/**
 * Metrics related DAO.
 */
public interface IMetricsDAO extends DAO {
    /**
     * Read data from the storage by given IDs.
     *
     * @param model     target entity of this query.
     * @param metrics   metrics list.
     * @return the data of all given IDs. Only include existing data. Don't require to keep the same order of ids list.
     * @throws IOException when error occurs in data query.
     */
    List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException;

    /**
     * Transfer the given metrics to an executable insert statement.
     *
     * @return InsertRequest should follow the database client driver datatype, in order to make sure it could be
     * executed ASAP.
     */
    InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException;

    /**
     * Transfer the given metrics to an executable update statement.
     *
     * @return UpdateRequest should follow the database client driver datatype, in order to make sure it could be
     * executed ASAP.
     */
    UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException;
}
