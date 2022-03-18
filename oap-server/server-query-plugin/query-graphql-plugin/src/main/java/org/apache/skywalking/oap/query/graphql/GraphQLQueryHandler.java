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

package org.apache.skywalking.oap.query.graphql;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.graphql.GraphqlService;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.schema.GraphQLSchema;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GraphQLQueryHandler {
    private final GraphqlService graphqlService;

    public GraphQLQueryHandler(
        final GraphQLQueryConfig config,
        final GraphQLSchema schema) {
        final int allowedComplexity = config.getMaxQueryComplexity();
        graphqlService =
            GraphqlService
                .builder()
                .schema(schema)
                .instrumentation(new MaxQueryComplexityInstrumentation(allowedComplexity, info -> {
                    log.warn(
                        "Aborting query because it's too complex, maximum allowed is [{}] but was [{}]",
                        allowedComplexity,
                        info.getComplexity());
                    return true;
                }))
                .build();
    }

    @Blocking
    @Post("/graphql")
    public HttpResponse graphql(
        final ServiceRequestContext ctx,
        final HttpRequest req) throws Exception {
        return graphqlService.serve(ctx, req);
    }
}
