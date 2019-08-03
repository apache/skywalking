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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * @author qxo
 */
public class EnhancedInstanceInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private final String[] classes;
 
    /**
     * @param classes - ie: 'packageA:classA1,classA2;packageB:classB1,classB2'
     */
    public EnhancedInstanceInstrumentation(String classes) {
        super();
        String[] groups =  classes.split(";");
        List<String> clsList = new ArrayList<String>();
        for (String g : groups) {
            final int idx = g.indexOf(':');
            final String prefix = idx != -1 ? g.substring(0, idx) : null;
            g = g.substring(idx + 1);
            String[] arr = g.split("[, ]+");
            for (String a : arr) {
                if (a.length() < 1) {
                    continue;
                }
                String cls = prefix == null ? a :  new StringBuilder(prefix).append('.').append(a).toString();
                clsList.add(cls);
            }
        }
        this.classes = new String[clsList.size()];
        clsList.toArray(this.classes);
    }

    @Override
    protected Builder<?> enhance(TypeDescription typeDescription, Builder<?> newClassBuilder, ClassLoader classLoader,
            EnhanceContext context) throws PluginException {
        Builder<?> builder = super.enhance(typeDescription, newClassBuilder, classLoader, context);
        if (!context.isObjectExtended()) {
            builder = builder.defineField(CONTEXT_ATTR_NAME, Object.class, Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE)
                .implement(EnhancedInstance.class)
                .intercept(FieldAccessor.ofField(CONTEXT_ATTR_NAME));
            context.extendObjectCompleted();
        }

        return builder;
    }
    
    @Override protected ClassMatch enhanceClass() {
        return MultiClassNameMatch.byMultiClassMatch(classes);
    }

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {};
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {};
    }
}