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

import com.linecorp.armeria.common.HttpMethod;
import graphql.kickstart.tools.SchemaParser;
import graphql.kickstart.tools.SchemaParserBuilder;
import graphql.scalars.ExtendedScalars;

import java.util.Collections;
import org.apache.skywalking.oap.query.graphql.resolver.AggregationQuery;
import org.apache.skywalking.oap.query.graphql.resolver.AlarmQuery;
import org.apache.skywalking.oap.query.graphql.resolver.AsyncProfilerMutation;
import org.apache.skywalking.oap.query.graphql.resolver.AsyncProfilerQuery;
import org.apache.skywalking.oap.query.graphql.resolver.BrowserLogQuery;
import org.apache.skywalking.oap.query.graphql.resolver.ContinuousProfilingMutation;
import org.apache.skywalking.oap.query.graphql.resolver.ContinuousProfilingQuery;
import org.apache.skywalking.oap.query.graphql.resolver.EBPFProcessProfilingMutation;
import org.apache.skywalking.oap.query.graphql.resolver.EBPFProcessProfilingQuery;
import org.apache.skywalking.oap.query.graphql.resolver.EventQuery;
import org.apache.skywalking.oap.query.graphql.resolver.HealthQuery;
import org.apache.skywalking.oap.query.graphql.resolver.HierarchyQuery;
import org.apache.skywalking.oap.query.graphql.resolver.LogQuery;
import org.apache.skywalking.oap.query.graphql.resolver.LogTestQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetadataQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetadataQueryV2;
import org.apache.skywalking.oap.query.graphql.resolver.MetricQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsExpressionQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.Mutation;
import org.apache.skywalking.oap.query.graphql.resolver.OndemandLogQuery;
import org.apache.skywalking.oap.query.graphql.resolver.ProfileMutation;
import org.apache.skywalking.oap.query.graphql.resolver.ProfileQuery;
import org.apache.skywalking.oap.query.graphql.resolver.Query;
import org.apache.skywalking.oap.query.graphql.resolver.RecordsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TopNRecordsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TopologyQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TraceQuery;
import org.apache.skywalking.oap.query.graphql.resolver.UIConfigurationManagement;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.QueryModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * GraphQL query provider.
 */
public class GraphQLQueryProvider extends ModuleProvider {
    protected GraphQLQueryConfig config;
    protected final SchemaParserBuilder schemaBuilder = SchemaParser.newParser();

    @Override
    public String name() {
        return "graphql";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return QueryModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<GraphQLQueryConfig>() {
            @Override
            public Class type() {
                return GraphQLQueryConfig.class;
            }

            @Override
            public void onInitialized(final GraphQLQueryConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        final MetadataQueryV2 metadataQueryV2 = new MetadataQueryV2(getManager());
        schemaBuilder.file("query-protocol/common.graphqls")
                     .resolvers(new Query(), new Mutation(), new HealthQuery(getManager()))
                     .file("query-protocol/metadata.graphqls")
                     .resolvers(new MetadataQuery(getManager()))
                     .file("query-protocol/topology.graphqls")
                     .resolvers(new TopologyQuery(getManager()))
                     /*
                      * Since 9.5.0.
                      * Metrics v3 query protocol is an enhanced metrics query(s) from original v1 and v2
                      * powered by newly added Metrics Query Expression Language to fetch and 
                      * manipulate metrics data in the query stage.
                      */
                     .file("query-protocol/metrics-v3.graphqls")
                     .resolvers(new MetricsExpressionQuery(getManager()))
                     ////////
                     //Deprecated Queries
                     ////////
                     .file("query-protocol/metric.graphqls")
                     .resolvers(new MetricQuery(getManager()))
                     .file("query-protocol/aggregation.graphqls")
                     .resolvers(new AggregationQuery(getManager()))
                     .file("query-protocol/top-n-records.graphqls")
                     .resolvers(new TopNRecordsQuery(getManager()))
                     //Deprecated since 9.5.0
                     .file("query-protocol/metrics-v2.graphqls")
                     .resolvers(new MetricsQuery(getManager()))
                     ////////
                     .file("query-protocol/trace.graphqls")
                     .resolvers(new TraceQuery(getManager()))
                     .file("query-protocol/alarm.graphqls")
                     .resolvers(new AlarmQuery(getManager()))
                     .file("query-protocol/log.graphqls")
                     .resolvers(
                         new LogQuery(getManager()),
                         new LogTestQuery(getManager(), config)
                     )
                     .file("query-protocol/profile.graphqls")
                     .resolvers(new ProfileQuery(getManager()), new ProfileMutation(getManager()))
                     .file("query-protocol/ui-configuration.graphqls")
                     .resolvers(new UIConfigurationManagement(getManager(), config))
                     .file("query-protocol/browser-log.graphqls")
                     .resolvers(new BrowserLogQuery(getManager()))
                     .file("query-protocol/event.graphqls")
                     .resolvers(new EventQuery(getManager()))
                     .file("query-protocol/metadata-v2.graphqls")
                     .resolvers(metadataQueryV2)
                     .file("query-protocol/ebpf-profiling.graphqls")
                     .resolvers(new EBPFProcessProfilingQuery(getManager()), new EBPFProcessProfilingMutation(getManager()))
                     .file("query-protocol/continuous-profiling.graphqls")
                     .resolvers(new ContinuousProfilingQuery(getManager()), new ContinuousProfilingMutation(getManager()))
                     .file("query-protocol/record.graphqls")
                     .resolvers(new RecordsQuery(getManager()))
                     .file("query-protocol/hierarchy.graphqls").resolvers(new HierarchyQuery(getManager()))
                .file("query-protocol/async-profiler.graphqls")
                .resolvers(new AsyncProfilerQuery(getManager()), new AsyncProfilerMutation(getManager()));

        if (config.isEnableOnDemandPodLog()) {
            schemaBuilder
                .file("query-protocol/ondemand-pod-log.graphqls")
                .resolvers(new OndemandLogQuery(metadataQueryV2));
        }

        schemaBuilder.scalars(ExtendedScalars.GraphQLLong);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        HTTPHandlerRegister service = getManager().find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(HTTPHandlerRegister.class);
        service.addHandler(
            new GraphQLQueryHandler(getManager(), config, schemaBuilder.build().makeExecutableSchema()),
            Collections.singletonList(HttpMethod.POST)
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
