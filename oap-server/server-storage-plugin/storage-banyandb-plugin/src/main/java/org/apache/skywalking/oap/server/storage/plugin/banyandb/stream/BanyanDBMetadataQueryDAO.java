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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic},
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic}
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic}
 * are all streams.
 */
public class BanyanDBMetadataQueryDAO extends AbstractBanyanDBDAO implements IMetadataQueryDAO {
    public BanyanDBMetadataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Service> getAllServices(String group) throws IOException {
        return query(Service.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                if (StringUtil.isNotEmpty(group)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ServiceTraffic.GROUP, group));
                }
            }
        });
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        return query(Service.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ServiceTraffic.NODE_TYPE, (long) NodeType.Browser.value()));
            }
        });
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        return query(Database.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ServiceTraffic.NODE_TYPE, (long) NodeType.Database.value()));
            }
        });
    }

    @Override
    public List<Service> searchServices(NodeType nodeType, String keyword) throws IOException {
        return query(Service.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ServiceTraffic.NODE_TYPE, (long) nodeType.value()));
            }
        }).stream().filter((s) -> s.getName().contains(keyword)) // TODO: support analyzer in database
                .collect(Collectors.toList());
    }

    @Override
    public Service searchService(NodeType nodeType, String serviceCode) throws IOException {
        return query(Service.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ServiceTraffic.NAME, serviceCode));
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ServiceTraffic.NODE_TYPE, (long) nodeType.value()));
                // only get one
                query.setLimit(1);
            }
        }).stream().findAny().orElse(null);
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
        return query(Endpoint.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", EndpointTraffic.SERVICE_ID, serviceId));
            }
        }).stream().filter((e) -> e.getName().contains(keyword))
                .limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
        return query(ServiceInstance.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                final long startMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
                final long endMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(endTimestamp);

                query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", InstanceTraffic.LAST_PING_TIME_BUCKET, startMinuteTimeBucket));
                query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", InstanceTraffic.LAST_PING_TIME_BUCKET, endMinuteTimeBucket));
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", InstanceTraffic.SERVICE_ID, serviceId));
            }
        });
    }
}
