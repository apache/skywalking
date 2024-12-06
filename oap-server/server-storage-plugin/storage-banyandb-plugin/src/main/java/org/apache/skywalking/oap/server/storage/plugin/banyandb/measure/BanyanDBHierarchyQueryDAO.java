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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

public class BanyanDBHierarchyQueryDAO extends AbstractBanyanDBDAO implements IHierarchyQueryDAO {
    private static final Set<String> SERVICE_HIERARCHY_RELATION_TAGS = ImmutableSet.of(
        ServiceHierarchyRelationTraffic.SERVICE_ID,
        ServiceHierarchyRelationTraffic.SERVICE_LAYER,
        ServiceHierarchyRelationTraffic.RELATED_SERVICE_ID,
        ServiceHierarchyRelationTraffic.RELATED_SERVICE_LAYER
    );

    private static final Set<String> INSTANCE_HIERARCHY_RELATION_TAGS = ImmutableSet.of(
        InstanceHierarchyRelationTraffic.INSTANCE_ID,
        InstanceHierarchyRelationTraffic.SERVICE_LAYER,
        InstanceHierarchyRelationTraffic.RELATED_INSTANCE_ID,
        InstanceHierarchyRelationTraffic.RELATED_SERVICE_LAYER
    );
    private final int limit;

    public BanyanDBHierarchyQueryDAO(final BanyanDBStorageClient client, BanyanDBStorageConfig config) {
        super(client);
        this.limit = config.getMetadataQueryMaxSize();
    }

    @Override
    public List<ServiceHierarchyRelationTraffic> readAllServiceHierarchyRelations() throws Exception {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ServiceHierarchyRelationTraffic.INDEX_NAME, DownSampling.Minute);
        MeasureQueryResponse resp = query(schema,
                                          SERVICE_HIERARCHY_RELATION_TAGS,
                                          Collections.emptySet(), new QueryBuilder<>() {
                @Override
                protected void apply(MeasureQuery query) {
                    query.limit(limit);
                }
            }
        );

        final List<ServiceHierarchyRelationTraffic> relations = new ArrayList<>();

        for (final DataPoint dataPoint : resp.getDataPoints()) {
            relations.add(new ServiceHierarchyRelationTraffic.Builder().storage2Entity(
                new BanyanDBConverter.StorageToMeasure(schema, dataPoint)));
        }

        return relations;
    }

    @Override
    public List<InstanceHierarchyRelationTraffic> readInstanceHierarchyRelations(final String instanceId,
                                                                                 final String layer) throws Exception {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ServiceHierarchyRelationTraffic.INDEX_NAME, DownSampling.Minute);
        MeasureQueryResponse resp = query(schema,
                                              INSTANCE_HIERARCHY_RELATION_TAGS,
                                          Collections.emptySet(), buildInstanceRelationsQuery(instanceId, layer)
        );

        List<InstanceHierarchyRelationTraffic> relations = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            relations.add(new InstanceHierarchyRelationTraffic.Builder().storage2Entity(
                new BanyanDBConverter.StorageToMeasure(schema, dataPoint)));
        }
        return relations;
    }

    private QueryBuilder<MeasureQuery> buildInstanceRelationsQuery(String instanceId, String layer) {
        int layerValue = Layer.valueOf(layer).value();
        return new QueryBuilder<>() {
            @Override
            protected void apply(MeasureQuery query) {
                List<AbstractCriteria> instanceRelationsQueryConditions = new ArrayList<>(2);

                instanceRelationsQueryConditions.add(
                    and(Lists.newArrayList(
                        eq(InstanceHierarchyRelationTraffic.INSTANCE_ID, instanceId),
                        eq(InstanceHierarchyRelationTraffic.SERVICE_LAYER, layerValue))
                    ));
                instanceRelationsQueryConditions.add(
                    and(Lists.newArrayList(
                        eq(InstanceHierarchyRelationTraffic.RELATED_INSTANCE_ID, instanceId),
                        eq(InstanceHierarchyRelationTraffic.RELATED_SERVICE_LAYER, layerValue)
                    ))
                );
                query.criteria(or(instanceRelationsQueryConditions));
            }
        };
    }
}
