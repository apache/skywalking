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

package org.apache.skywalking.apm.ui.protocol;

import graphql.schema.idl.EchoingWiringFactory;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.File;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GraphQLScriptTest {
    
    @Test
    public void assertScriptFormat() {
        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
    
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        typeRegistry.merge(schemaParser.parse(loadSchema("common.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("trace.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("overview-layer.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("application-layer.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("server-layer.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("service-layer.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("alarm.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("config.graphqls")));
        RuntimeWiring wiring = buildRuntimeWiring();
        assertTrue(schemaGenerator.makeExecutableSchema(typeRegistry, wiring).getAllTypesAsList().size() > 0);
    }
    
    private File loadSchema(final String s) {
        return new File(GraphQLScriptTest.class.getClassLoader().getResource("ui-graphql/" + s).getFile());
    }
    
    private RuntimeWiring buildRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring().wiringFactory(new EchoingWiringFactory()).build();
    }
}
