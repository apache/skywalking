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

package org.apache.skywalking.oap.log.analyzer.dsl;

import groovy.lang.GroovyShell;
import groovy.transform.CompileStatic;
import groovy.util.DelegatingScript;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.LALDelegatingScript;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DSL {
    private final DelegatingScript script;

    private final FilterSpec filterSpec;

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl) throws ModuleStartException {
        final CompilerConfiguration cc = new CompilerConfiguration();
        final ASTTransformationCustomizer customizer =
            new ASTTransformationCustomizer(
                singletonMap(
                    "extensions",
                    singletonList(LALPrecompiledExtension.class.getName())
                ),
                CompileStatic.class
            );
        cc.addCompilationCustomizers(customizer);
        cc.setScriptBaseClass(LALDelegatingScript.class.getName());

        final GroovyShell sh = new GroovyShell(cc);
        final DelegatingScript script = (DelegatingScript) sh.parse(dsl);
        final FilterSpec filterSpec = new FilterSpec(moduleManager, config);
        script.setDelegate(filterSpec);

        return new DSL(script, filterSpec);
    }

    public void bind(final Binding binding) {
        this.filterSpec.bind(binding);
    }

    public void evaluate() {
        script.run();
    }
}
