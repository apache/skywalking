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

import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

import static org.codehaus.groovy.ast.ClassHelper.makeCached;

public class LALPrecompiledExtension extends AbstractTypeCheckingExtension {

    public LALPrecompiledExtension(final StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
    }

    @Override
    public boolean handleUnresolvedProperty(final PropertyExpression pexp) {
        final Expression exp = pexp.getObjectExpression();

        if (exp.getText().startsWith("parsed")) {
            makeDynamic(pexp);
            setHandled(true);
            return true;
        }

        if (exp.getText().startsWith("log")) {
            if (handleLogVariable(pexp)) {
                return true;
            }
        }

        return super.handleUnresolvedProperty(pexp);
    }

    private boolean handleLogVariable(final PropertyExpression pexp) {
        final Expression exp = pexp.getObjectExpression();
        final Expression p = pexp.getProperty();

        if (exp instanceof VariableExpression) {
            final VariableExpression v = (VariableExpression) exp;
            if (v.getName().equals("log")) {
                storeType(v, makeCached(LogData.Builder.class));
            }
            if (p instanceof ConstantExpression) {
                final ConstantExpression c = (ConstantExpression) p;
                switch (c.getText()) {
                    case "body":
                        storeType(pexp, makeCached(LogDataBody.class));
                        break;
                    case "traceContext":
                        storeType(pexp, makeCached(TraceContext.class));
                        break;
                    case "tags":
                        storeType(pexp, makeCached(LogTags.class));
                        break;
                }
            }
            setHandled(true);
            return true;
        }

        return false;
    }
}

