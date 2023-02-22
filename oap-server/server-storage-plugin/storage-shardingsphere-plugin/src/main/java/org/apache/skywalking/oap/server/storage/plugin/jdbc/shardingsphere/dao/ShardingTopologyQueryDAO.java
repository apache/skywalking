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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;

public class ShardingTopologyQueryDAO extends JDBCTopologyQueryDAO {

    public ShardingTopologyQueryDAO(JDBCClient h2Client) {
        super(h2Client);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration,
                                                                          List<String> serviceIds) throws IOException {
        return super.loadServiceRelationsDetectedAtServerSide(DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration), serviceIds);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                         List<String> serviceIds) throws IOException {
        return super.loadServiceRelationDetectedAtClientSide(DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration), serviceIds);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException {
        return super.loadServiceRelationsDetectedAtServerSide(DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException {
        return super.loadServiceRelationDetectedAtClientSide(DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return super.loadInstanceRelationDetectedAtServerSide(clientServiceId, serverServiceId,
                                                              DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          Duration duration) throws IOException {
        return super.loadInstanceRelationDetectedAtClientSide(clientServiceId, serverServiceId,
                                                              DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(Duration duration,
                                                      String destEndpointId) throws IOException {
        return super.loadEndpointRelation(DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration),
                                          destEndpointId);
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId, Duration duration) throws IOException {
        return super.loadProcessRelationDetectedAtClientSide(serviceInstanceId,
                                                             DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId, Duration duration) throws IOException {
        return super.loadProcessRelationDetectedAtServerSide(serviceInstanceId,
                                                             DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }
}
