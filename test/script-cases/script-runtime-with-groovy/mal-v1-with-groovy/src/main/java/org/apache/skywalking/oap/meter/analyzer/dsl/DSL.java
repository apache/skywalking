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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableList;
import groovy.lang.Binding;
import groovy.lang.GString;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.oap.meter.analyzer.dsl.registry.ProcessRegistry;
import org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.K8sRetagType;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

/**
 * DSL combines methods to parse groovy based DSL expression.
 */
public final class DSL {

    /**
     * Parse string literal to Expression object, which can be reused.
     *
     * @param metricName the name of metric defined in mal rule
     * @param expression string literal represents the DSL expression.
     * @return Expression object could be executed.
     */
    public static Expression parse(final String metricName, final String expression) {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(DelegatingScript.class.getName());
        ImportCustomizer icz = new ImportCustomizer();
        icz.addImport("K8sRetagType", K8sRetagType.class.getName());
        icz.addImport("DetectPoint", DetectPoint.class.getName());
        icz.addImport("Layer", Layer.class.getName());
        icz.addImport("ProcessRegistry", ProcessRegistry.class.getName());
        cc.addCompilationCustomizers(icz);

        final SecureASTCustomizer secureASTCustomizer = new SecureASTCustomizer();
        secureASTCustomizer.setDisallowedStatements(
            ImmutableList.<Class<? extends Statement>>builder()
                         .add(WhileStatement.class)
                         .add(DoWhileStatement.class)
                         .add(ForStatement.class)
                         .build());
        // noinspection rawtypes
        secureASTCustomizer.setAllowedReceiversClasses(
            ImmutableList.<Class>builder()
                         .add(Object.class)
                         .add(Map.class)
                         .add(List.class)
                         .add(Array.class)
                         .add(K8sRetagType.class)
                         .add(DetectPoint.class)
                         .add(Layer.class)
                         .add(ProcessRegistry.class)
                         .add(GString.class)
                         .add(String.class)
                .build());
        cc.addCompilationCustomizers(secureASTCustomizer);

        GroovyShell sh = new GroovyShell(new Binding(), cc);
        DelegatingScript script = (DelegatingScript) sh.parse(expression);
        return new Expression(metricName, expression, script);
    }
}
