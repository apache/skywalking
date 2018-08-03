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
import org.apache.skywalking.oap.query.graphql.resolver.AggregationQuery;
import org.apache.skywalking.oap.query.graphql.resolver.AlarmQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetadataQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetricQuery;
import org.apache.skywalking.oap.query.graphql.resolver.Mutation;
import org.apache.skywalking.oap.query.graphql.resolver.Query;
import org.apache.skywalking.oap.query.graphql.resolver.TopologyQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TraceQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.QueryModule;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

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
            .file("ui-graphql-v6/common.graphqls")
            .resolvers(new Query(), new Mutation())
            .file("ui-graphql-v6/metadata.graphqls")
            .resolvers(new MetadataQuery())
            .file("ui-graphql-v6/metric.graphqls")
            .resolvers(new MetricQuery())
            .file("ui-graphql-v6/topology.graphqls")
            .resolvers(new TopologyQuery())
            .file("ui-graphql-v6/trace.graphqls")
            .resolvers(new TraceQuery())
            .file("ui-graphql-v6/aggregation.graphqls")
            .resolvers(new AggregationQuery())
            .file("ui-graphql-v6/alarm.graphqls")
            .resolvers(new AlarmQuery())
            .build()
            .makeExecutableSchema();
        this.graphQL = GraphQL.newGraphQL(schema).build();
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        JettyHandlerRegister service = getManager().find(CoreModule.NAME).getService(JettyHandlerRegister.class);
        service.addHandler(new GraphQLQueryHandler(config.getPath(), graphQL));
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
