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

import com.coxautodev.graphql.tools.SchemaParser;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.apache.skywalking.oap.query.graphql.resolver.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.QueryModule;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * GraphQL query provider.
 *
 * @author gaohongtao
 */
public class GraphQLQueryProvider extends ModuleProvider {

    private final GraphQLQueryConfig config = new GraphQLQueryConfig();

    private GraphQL graphQL;

    @Override public String name() {
        return "graphql";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return QueryModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        GraphQLSchema schema = SchemaParser.newParser()
            .file("query-protocol/common.graphqls")
            .resolvers(new Query(), new Mutation())
            .file("query-protocol/metadata.graphqls")
            .resolvers(new MetadataQuery(getManager()))
            .file("query-protocol/metric.graphqls")
            .resolvers(new MetricQuery(getManager()))
            .file("query-protocol/topology.graphqls")
            .resolvers(new TopologyQuery(getManager()))
            .file("query-protocol/trace.graphqls")
            .resolvers(new TraceQuery(getManager()))
            .file("query-protocol/aggregation.graphqls")
            .resolvers(new AggregationQuery(getManager()))
            .file("query-protocol/alarm.graphqls")
            .resolvers(new AlarmQuery(getManager()))
            .file("query-protocol/top-n-records.graphqls")
            .resolvers(new TopNRecordsQuery(getManager()))
            .build()
            .makeExecutableSchema();
        this.graphQL = GraphQL.newGraphQL(schema).build();
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        JettyHandlerRegister service = getManager().find(CoreModule.NAME).provider().getService(JettyHandlerRegister.class);
        service.addHandler(new GraphQLQueryHandler(config.getPath(), graphQL));
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
