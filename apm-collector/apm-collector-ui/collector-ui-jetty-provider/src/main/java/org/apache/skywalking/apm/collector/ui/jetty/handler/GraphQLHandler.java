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

package org.apache.skywalking.apm.collector.ui.jetty.handler;

import com.coxautodev.graphql.tools.SchemaParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.storage.ui.application.ApplicationNode;
import org.apache.skywalking.apm.collector.storage.ui.application.ConjecturalNode;
import org.apache.skywalking.apm.collector.storage.ui.common.VisualUserNode;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceNode;
import org.apache.skywalking.apm.collector.ui.graphql.VersionMutation;
import org.apache.skywalking.apm.collector.ui.graphql.VersionQuery;
import org.apache.skywalking.apm.collector.ui.mutation.ConfigMutation;
import org.apache.skywalking.apm.collector.ui.query.AlarmQuery;
import org.apache.skywalking.apm.collector.ui.query.ApplicationQuery;
import org.apache.skywalking.apm.collector.ui.query.ConfigQuery;
import org.apache.skywalking.apm.collector.ui.query.OverViewLayerQuery;
import org.apache.skywalking.apm.collector.ui.query.ServerQuery;
import org.apache.skywalking.apm.collector.ui.query.ServiceQuery;
import org.apache.skywalking.apm.collector.ui.query.TraceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GraphQLHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(GraphQLHandler.class);

    private final Gson gson = new Gson();
    private final GraphQL graphQL;
    private static final String QUERY = "query";
    private static final String VARIABLES = "variables";
    private static final String DATA = "data";
    private static final String ERRORS = "errors";
    private static final String MESSAGE = "message";

    public GraphQLHandler(ModuleManager moduleManager) {
        GraphQLSchema schema = SchemaParser.newParser()
            .file("ui-graphql/alarm.graphqls")
            .file("ui-graphql/application-layer.graphqls")
            .file("ui-graphql/common.graphqls")
            .file("ui-graphql/config.graphqls")
            .file("ui-graphql/overview-layer.graphqls")
            .file("ui-graphql/server-layer.graphqls")
            .file("ui-graphql/service-layer.graphqls")
            .file("ui-graphql/trace.graphqls")
            .resolvers(new VersionQuery(), new VersionMutation(), new AlarmQuery(moduleManager), new ApplicationQuery(moduleManager))
            .resolvers(new OverViewLayerQuery(moduleManager), new ServerQuery(moduleManager), new ServiceQuery(moduleManager), new TraceQuery(moduleManager))
            .resolvers(new ConfigQuery(), new ConfigMutation())
            .dictionary(ConjecturalNode.class, VisualUserNode.class, ApplicationNode.class, ServiceNode.class)
            .build()
            .makeExecutableSchema();

        this.graphQL = GraphQL.newGraphQL(schema).build();
    }

    @Override public String pathSpec() {
        return "/graphql";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        return execute(req.getParameter(QUERY), null);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
        String line;
        String request = "";
        while ((line = reader.readLine()) != null) {
            request += line;
        }

        JsonObject requestJson = gson.fromJson(request, JsonObject.class);

        Type mapType = new TypeToken<Map<String, Object>>() { }.getType();

        return execute(requestJson.get(QUERY).getAsString(), gson.fromJson(requestJson.get(VARIABLES), mapType));
    }

    private JsonObject execute(String request, Map<String, Object> variables) {
        try {
            ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(request).variables(variables).build();
            ExecutionResult executionResult = graphQL.execute(executionInput);
            logger.info("Execution result is {}", executionResult);
            Object data = executionResult.getData();
            List<GraphQLError> errors = executionResult.getErrors();

            JsonObject jsonObject = new JsonObject();
            if (data != null) {
                jsonObject.add(DATA, gson.fromJson(gson.toJson(data), JsonObject.class));
            }

            if (CollectionUtils.isNotEmpty(errors)) {
                JsonArray errorArray = new JsonArray();
                errors.forEach(error -> {
                    JsonObject errorJson = new JsonObject();
                    errorJson.addProperty(MESSAGE, error.getMessage());
                    errorArray.add(errorJson);
                });

                jsonObject.add(ERRORS, errorArray);
            }

            return jsonObject;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            JsonObject jsonObject = new JsonObject();

            JsonArray errorArray = new JsonArray();
            JsonObject errorJson = new JsonObject();
            errorJson.addProperty(MESSAGE, e.getMessage());
            errorArray.add(errorJson);

            jsonObject.add(ERRORS, errorArray);
            return jsonObject;
        }
    }
}
