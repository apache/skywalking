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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.graphql.GraphQLCreator;

/**
 * @author peng-yongsheng
 */
public class GraphQLHandler extends JettyHandler {

    private final Gson gson = new Gson();
    private final GraphQL graphQL;
    private static final String QUERY = "query";
    private static final String DATA = "data";
    private static final String ERRORS = "errors";

    public GraphQLHandler() {
        GraphQLCreator creator = new GraphQLCreator();
        this.graphQL = creator.create();
    }

    @Override public String pathSpec() {
        return "/graphql";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        return execute(req.getParameter(QUERY));
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
        String line;
        String request = "";
        while ((line = reader.readLine()) != null) {
            request += line;
        }

        return execute(request);
    }

    private JsonObject execute(String request) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(request).build();
        ExecutionResult executionResult = graphQL.execute(executionInput);

        Object data = executionResult.getData();
        List<GraphQLError> errors = executionResult.getErrors();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(DATA, data.toString());

        if (CollectionUtils.isNotEmpty(errors)) {
            String errorJsonStr = gson.toJson(errors, JsonArray.class);
            JsonArray errorArray = gson.fromJson(errorJsonStr, JsonArray.class);
            jsonObject.add(ERRORS, errorArray);
        }

        return jsonObject;
    }
}
