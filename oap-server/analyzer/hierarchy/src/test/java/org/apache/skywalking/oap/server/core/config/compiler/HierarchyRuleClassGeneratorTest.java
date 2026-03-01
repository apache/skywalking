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

package org.apache.skywalking.oap.server.core.config.compiler;

import java.util.function.BiFunction;
import javassist.ClassPool;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyRuleClassGeneratorTest {

    private HierarchyRuleClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HierarchyRuleClassGenerator(new ClassPool(true));
    }

    @Test
    void compileSimpleNameEquality() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "name", "{ (u, l) -> u.name == l.name }");

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setName("svc-a");
        final Service lower = new Service();
        lower.setName("svc-a");
        assertTrue(fn.apply(upper, lower));

        lower.setName("svc-b");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileShortNameEquality() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "short-name", "{ (u, l) -> u.shortName == l.shortName }");

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc");
        final Service lower = new Service();
        lower.setShortName("svc");
        assertTrue(fn.apply(upper, lower));

        lower.setShortName("other");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileLowerShortNameRemoveNs() throws Exception {
        final String expr = "{ (u, l) -> {"
            + " if (l.shortName.lastIndexOf('.') > 0) {"
            + "   return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.'));"
            + " }"
            + " return false;"
            + "} }";
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "lower-short-name-remove-ns", expr);

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc-a");
        final Service lower = new Service();
        lower.setShortName("svc-a.ns1");
        assertTrue(fn.apply(upper, lower));

        lower.setShortName("svc-b.ns1");
        assertFalse(fn.apply(upper, lower));

        lower.setShortName("no-dot");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileLowerShortNameWithFqdn() throws Exception {
        final String expr = "{ (u, l) -> {"
            + " if (u.shortName.lastIndexOf(':') > 0) {"
            + "   return u.shortName.substring(0, u.shortName.lastIndexOf(':'))"
            + "     == l.shortName.concat('.svc.cluster.local');"
            + " }"
            + " return false;"
            + "} }";
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "lower-short-name-with-fqdn", expr);

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc-a.svc.cluster.local:8080");
        final Service lower = new Service();
        lower.setShortName("svc-a");
        assertTrue(fn.apply(upper, lower));

        upper.setShortName("no-port");
        assertFalse(fn.apply(upper, lower));
    }

    // ==================== Error handling tests ====================

    @Test
    void emptyExpressionThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:0 mismatched input '<EOF>'
        //   expecting '{'
        assertThrows(Exception.class,
            () -> generator.compile("empty", ""));
    }

    @Test
    void missingClosureBracesThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:0 mismatched input 'u'
        //   expecting '{'
        assertThrows(Exception.class,
            () -> generator.compile("test", "u.name == l.name"));
    }

    @Test
    void missingParametersThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:2 mismatched input '}'
        //   expecting '('
        assertThrows(Exception.class,
            () -> generator.compile("test", "{ }"));
    }

    @Test
    void invalidFieldAccessThrows() {
        // Demo error: [source error] getNonExistent() not found in Service
        // (Javassist cannot find the getter for a non-existent field)
        assertThrows(Exception.class,
            () -> generator.compile("test",
                "{ (u, l) -> u.nonExistent == l.nonExistent }"));
    }
}
