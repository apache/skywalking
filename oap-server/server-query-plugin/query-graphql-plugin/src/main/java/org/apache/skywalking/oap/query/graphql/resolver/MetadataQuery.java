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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.util.List;
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class MetadataQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private MetadataQueryService metadataQueryService;

    public MetadataQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private MetadataQueryService getMetadataQueryService() {
        if (metadataQueryService == null) {
            this.metadataQueryService = moduleManager.find(CoreModule.NAME).getService(MetadataQueryService.class);
        }
        return metadataQueryService;
    }

    public ClusterBrief getGlobalBrief(final Duration duration) {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetadataQueryService().getGlobalBrief(duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public List<Service> getAllServices(final Duration duration) {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetadataQueryService().getAllServices(duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public List<Service> searchServices(final Duration duration, final String keyword) {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetadataQueryService().searchServices(duration.getStep(), startTimeBucket, endTimeBucket, keyword);
    }

    public List<ServiceInstance> getServiceInstances(final Duration duration, final String id) {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetadataQueryService().getServiceInstances(duration.getStep(), startTimeBucket, endTimeBucket, id);
    }

    public List<Endpoint> searchEndpoint(final String keyword, final String serviceId, final int limit) {
        return getMetadataQueryService().searchEndpoint(keyword, serviceId, limit);
    }

    public Service searchService(final Duration duration, final String serviceCode) {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getMetadataQueryService().searchService(duration.getStep(), startTimeBucket, endTimeBucket, serviceCode);
    }
}
