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

package org.apache.skywalking.banyandb.client;

import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;

import java.util.List;

/**
 * BanyanDBTraceService exposes trace APIs provided by the BanyanDB.
 */
public interface BanyanDBTraceService {
    /**
     * API for searching for traces with given conditions
     *
     * @param request BanyanDB search request to do complex search
     * @return query response containing entities which satisfy the given query condtions
     */
    BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request);

    /**
     * API for fetching given TraceId
     *
     * @param traceFetchRequest request with traceId
     * @return query response with entities belonging to the given traceId
     */
    BanyanDBQueryResponse queryByTraceId(TraceFetchRequest traceFetchRequest);

    /**
     * Batch Trace Write API for BanyanBD
     *
     * @param data list of write requsts
     */
    void writeEntity(List<TraceWriteRequest> data);
}
