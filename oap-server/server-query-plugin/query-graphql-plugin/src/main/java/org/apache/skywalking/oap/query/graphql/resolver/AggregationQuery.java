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
import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class AggregationQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private AggregationQueryService queryService;

    public AggregationQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AggregationQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(AggregationQueryService.class);
        }
        return queryService;
    }

    public List<TopNEntity> getServiceTopN(final String name, final int topN, final Duration duration,
        final Order order) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getServiceTopN(name, topN, duration.getStep(), startTimeBucket, endTimeBucket, order);
    }

    public List<TopNEntity> getAllServiceInstanceTopN(final String name, final int topN, final Duration duration,
        final Order order) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getAllServiceInstanceTopN(name, topN, duration.getStep(), startTimeBucket, endTimeBucket, order);
    }

    public List<TopNEntity> getServiceInstanceTopN(final int serviceId, final String name, final int topN,
        final Duration duration, final Order order) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getServiceInstanceTopN(serviceId, name, topN, duration.getStep(), startTimeBucket, endTimeBucket, order);
    }

    public List<TopNEntity> getAllEndpointTopN(final String name, final int topN,
        final Duration duration, final Order order) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getAllEndpointTopN(name, topN, duration.getStep(), startTimeBucket, endTimeBucket, order);
    }

    public List<TopNEntity> getEndpointTopN(final int serviceId, final String name, final int topN,
        final Duration duration, final Order order) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getEndpointTopN(serviceId, name, topN, duration.getStep(), startTimeBucket, endTimeBucket, order);
    }
}
