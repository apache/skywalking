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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BanyanDBTopologyQueryDAO extends AbstractBanyanDBDAO implements ITopologyQueryDAO {
    public BanyanDBTopologyQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        if (CollectionUtils.isEmpty(serviceIds)) {
            throw new UnexpectedException("Service id is empty");
        }

        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID, serviceIds, DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationServerSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB) throws IOException {
        return loadServiceCalls(
                ServiceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceRelationClientSideMetrics.DEST_SERVICE_ID, new ArrayList<>(0), DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
                DetectPoint.SERVER
        );
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return loadServiceInstanceCalls(
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME, startTB, endTB,
                ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, clientServiceId, serverServiceId,
                DetectPoint.CLIENT
        );
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB, long endTB, String destEndpointId) throws IOException {
        List<Call.CallDetail> calls = loadEndpointFromSide(
                EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, false
        );
        calls.addAll(
                loadEndpointFromSide(EndpointRelationServerSideMetrics.INDEX_NAME, startTB, endTB,
                        EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
                        EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId, true
                ));
        return calls;
    }

    private List<Call.CallDetail> loadServiceCalls(String tableName,
                                                   long startTB,
                                                   long endTB,
                                                   String sourceCName,
                                                   String destCName,
                                                   List<String> serviceIds,
                                                   DetectPoint detectPoint) throws IOException {
        // TODO: we will impl this method after we support `OR`
        return Collections.emptyList();
    }

    private List<Call.CallDetail> loadServiceInstanceCalls(String tableName,
                                                           long startTB,
                                                           long endTB,
                                                           String sourceCName,
                                                           String descCName,
                                                           String sourceServiceId,
                                                           String destServiceId,
                                                           DetectPoint detectPoint) throws IOException {
        // TODO: we will impl this method after we support `OR`
        return Collections.emptyList();
    }

    private List<Call.CallDetail> loadEndpointFromSide(String tableName,
                                                       long startTB,
                                                       long endTB,
                                                       String sourceCName,
                                                       String destCName,
                                                       String id,
                                                       boolean isSourceId) throws IOException {
        // TODO: we will impl this method after we support `OR`
        return Collections.emptyList();
    }
}
