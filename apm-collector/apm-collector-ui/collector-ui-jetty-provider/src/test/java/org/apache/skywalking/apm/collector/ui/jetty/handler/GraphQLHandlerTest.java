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
 */

package org.apache.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import graphql.GraphQLError;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.ui.DelegatingServletInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author lican
 */
public class GraphQLHandlerTest {

    private GraphQLHandler graphQLHandler;
    private GraphQL graphQL;
    private String mockParams = "{\"query\":\"query\" }";
    private static final String DATA = "data";
    private static final String ERRORS = "errors";
    private Map<String, String> stringStringMap = Collections.singletonMap("something", "test");


    @Before
    public void setUp() {
        //test if the constructor is well
        graphQLHandler = new GraphQLHandler(null);
        //stub graphQL
        graphQL = Mockito.mock(GraphQL.class);
        Whitebox.setInternalState(graphQLHandler, "graphQL", graphQL);
    }

    @Test
    public void pathSpec() {
        Assert.assertEquals("/graphql", graphQLHandler.pathSpec());
    }

    @Test
    public void doGet() throws ArgumentsParseException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter(anyString())).then(invocation -> mockParams);
        when(graphQL.execute((ExecutionInput) anyObject())).then(invocation -> new ExecutionResultImpl(stringStringMap, null, Collections.emptyMap()));
        JsonElement jsonElement = graphQLHandler.doGet(req);
        Assert.assertNotNull(((JsonObject) jsonElement).get(DATA));
    }

    @Test
    public void doPost() throws IOException, ArgumentsParseException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getInputStream()).then(invocation -> new DelegatingServletInputStream(new ByteArrayInputStream(mockParams.getBytes())));
        when(graphQL.execute((ExecutionInput) anyObject())).then(invocation -> new ExecutionResultImpl(stringStringMap, null, Collections.emptyMap()));
        JsonElement jsonElement = graphQLHandler.doPost(req);
        Assert.assertNotNull(((JsonObject) jsonElement).get(DATA));
        Assert.assertNull(((JsonObject) jsonElement).get(ERRORS));
        when(graphQL.execute((ExecutionInput) anyObject())).then(invocation -> {
            GraphQLError graphQLError = Mockito.mock(GraphQLError.class);
            return new ExecutionResultImpl(stringStringMap, Collections.singletonList(graphQLError), Collections.emptyMap());
        });
        jsonElement = graphQLHandler.doPost(req);
        Assert.assertNotNull(((JsonObject) jsonElement).get(ERRORS));
        //test exception;
        when(graphQL.execute((ExecutionInput) anyObject())).then(invocation -> {
            throw new IllegalArgumentException("unit test exception when execute");
        });
        jsonElement = graphQLHandler.doPost(req);
        Assert.assertNotNull(((JsonObject) jsonElement).get(ERRORS));


    }
}