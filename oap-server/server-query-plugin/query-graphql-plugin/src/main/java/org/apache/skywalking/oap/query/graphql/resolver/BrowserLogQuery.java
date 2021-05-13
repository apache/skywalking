/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.BrowserLogQueryService;
import org.apache.skywalking.oap.server.core.query.input.BrowserErrorLogQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
public class BrowserLogQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private BrowserLogQueryService queryService;

    private BrowserLogQueryService getQueryService() {
        return Optional.ofNullable(queryService).orElseGet(() -> {
            queryService = moduleManager.find(CoreModule.NAME).provider().getService(BrowserLogQueryService.class);
            return queryService;
        });
    }

    public BrowserErrorLogs queryBrowserErrorLogs(BrowserErrorLogQueryCondition condition) throws IOException {
        long startSecondTB = 0, endSecondTB = 0;
        if (nonNull(condition.getQueryDuration())) {
            startSecondTB = condition.getQueryDuration()
                                     .getStartTimeBucketInSec();
            endSecondTB = condition.getQueryDuration()
                                   .getEndTimeBucketInSec();
        }

        return getQueryService().queryBrowserErrorLogs(
            condition.getServiceId(), condition.getServiceVersionId(), condition.getPagePathId(),
            condition.getPagePath(), condition.getCategory()
            , startSecondTB, endSecondTB, condition.getPaging()
        );
    }
}
