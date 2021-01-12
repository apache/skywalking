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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.library.module.Service;

public interface ITopologyQueryDAO extends Service {
    /**
     * Query {@link ServiceRelationServerSideMetrics} through the given conditions
     */
    List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB,
                                                                   List<String> serviceIds) throws IOException;

    /**
     * Query {@link ServiceRelationClientSideMetrics} through the given conditions
     */
    List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB,
                                                                  List<String> serviceIds) throws IOException;

    /**
     * Query {@link ServiceRelationServerSideMetrics} globally, without given serviceIds
     */
    List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB,
                                                                   long endTB) throws IOException;

    /**
     * Query {@link ServiceRelationClientSideMetrics} globally, without given serviceIds
     */
    List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB,
                                                                  long endTB) throws IOException;

    /**
     * Query {@link ServiceInstanceRelationServerSideMetrics} through given conditions, including the specific
     * clientServiceId and serverServiceId
     */
    List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                   String serverServiceId,
                                                                   long startTB,
                                                                   long endTB) throws IOException;

    /**
     * Query {@link ServiceInstanceRelationClientSideMetrics} through given conditions, including the specific
     * clientServiceId and serverServiceId
     */
    List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                   String serverServiceId,
                                                                   long startTB,
                                                                   long endTB) throws IOException;

    /**
     * Query the endpoint relationship. Endpoint dependency is not detected from server side agent.
     */
    List<Call.CallDetail> loadEndpointRelation(long startTB,
                                               long endTB,
                                               String destEndpointId) throws IOException;
}
