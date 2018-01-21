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

package org.apache.skywalking.apm.collector.ui.graphql;

import com.coxautodev.graphql.tools.SchemaParser;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.apache.skywalking.apm.collector.ui.graphql.alarm.AlarmQuery;
import org.apache.skywalking.apm.collector.ui.graphql.application.ApplicationQuery;
import org.apache.skywalking.apm.collector.ui.graphql.application.ConjecturalNode;
import org.apache.skywalking.apm.collector.ui.graphql.common.VisualUserNode;
import org.apache.skywalking.apm.collector.ui.graphql.config.ConfigMutation;
import org.apache.skywalking.apm.collector.ui.graphql.config.ConfigQuery;
import org.apache.skywalking.apm.collector.ui.graphql.overview.OverViewLayerQuery;
import org.apache.skywalking.apm.collector.ui.graphql.server.ServerQuery;
import org.apache.skywalking.apm.collector.ui.graphql.service.ServiceQuery;
import org.apache.skywalking.apm.collector.ui.graphql.trace.TraceQuery;

/**
 * @author peng-yongsheng
 */
public class GraphQLCreator {

    public GraphQL create() {
        GraphQLSchema schema = SchemaParser.newParser()
            .file("ui-graphql/alarm.graphqls")
            .file("ui-graphql/application-layer.graphqls")
            .file("ui-graphql/common.graphqls")
            .file("ui-graphql/config.graphqls")
            .file("ui-graphql/overview-layer.graphqls")
            .file("ui-graphql/server-layer.graphqls")
            .file("ui-graphql/service-layer.graphqls")
            .file("ui-graphql/trace.graphqls")
            .resolvers(new VersionQuery(), new VersionMutation(), new AlarmQuery(), new ApplicationQuery())
            .resolvers(new OverViewLayerQuery(), new ServerQuery(), new ServiceQuery(), new TraceQuery())
            .resolvers(new ConfigQuery(), new ConfigMutation())
            .dictionary(ConjecturalNode.class, VisualUserNode.class)
            .build()
            .makeExecutableSchema();

        return GraphQL.newGraphQL(schema).build();
    }
}
