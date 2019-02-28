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
import java.text.ParseException;
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.*;
import org.apache.skywalking.oap.server.core.query.entity.Topology;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class TopologyQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TopologyQueryService queryService;

    public TopologyQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TopologyQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TopologyQueryService.class);
        }
        return queryService;
    }

    public Topology getGlobalTopology(final Duration duration) throws IOException, ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startTimestamp = DurationUtils.INSTANCE.startTimeToTimestamp(duration.getStep(), duration.getStart());
        long endTimestamp = DurationUtils.INSTANCE.endTimeToTimestamp(duration.getStep(), duration.getEnd());

        return getQueryService().getGlobalTopology(duration.getStep(), startTimeBucket, endTimeBucket, startTimestamp, endTimestamp);
    }

    public Topology getServiceTopology(final int serviceId, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getServiceTopology(duration.getStep(), startTimeBucket, endTimeBucket, serviceId);
    }

    public Topology getEndpointTopology(final int endpointId, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getEndpointTopology(duration.getStep(), startTimeBucket, endTimeBucket, endpointId);
    }
}
