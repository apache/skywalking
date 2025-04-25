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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

public class BanyanDBServiceLabelDAO extends AbstractBanyanDBDAO implements IServiceLabelDAO {
    private static final Set<String> TAGS = ImmutableSet.of(ServiceLabelRecord.LABEL, ServiceLabelRecord.SERVICE_ID);
    private final int limit;

    public BanyanDBServiceLabelDAO(final BanyanDBStorageClient client, BanyanDBStorageConfig config) {
        super(client);
        this.limit = config.getGlobal().getMetadataQueryMaxSize();
    }

    @Override
    public List<String> queryAllLabels(String serviceId) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ServiceLabelRecord.INDEX_NAME, DownSampling.Minute);
        return query(false, schema, TAGS,
                Collections.emptySet(), new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(final MeasureQuery query) {
                        query.and(eq(ServiceLabelRecord.SERVICE_ID, serviceId));
                        query.limit(limit);
                    }
                }).getDataPoints()
                .stream()
                .map(point -> (String) point.getTagValue(ServiceLabelRecord.LABEL))
                .collect(Collectors.toList());
    }
}
