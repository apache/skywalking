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


package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstanceInstrumentation;
import org.apache.skywalking.apm.util.StringUtil;

public class PluginDefine {
    /**
     * Plugin name.
     */
    private String name;

    /**
     * The class name of plugin defined.
     */
    private String defineClass;
    
    /**
     * The  string argument of defineClass  constructor
     */
    private final String constructorArgument;

    public String getConstructorArgument() {
        return constructorArgument;
    }

    private PluginDefine(String name, String defineClass, String constructorArgument) {
        this.name = name;
        this.defineClass = defineClass;
        this.constructorArgument = constructorArgument;
    }

    public static PluginDefine build(String define) throws IllegalPluginDefineException {
        if (StringUtil.isEmpty(define)) {
            throw new IllegalPluginDefineException(define);
        }
        final int defineIdx = define.indexOf('=');
        if (defineIdx == -1) {
            throw new IllegalPluginDefineException(define);
        }
        String pluginName = define.substring(0, defineIdx);
        String defineClass = define.substring(defineIdx + 1);
        String arg = null;
        if (pluginName.endsWith("enhancedInstanceClasses")) {
            arg = defineClass;
            defineClass = EnhancedInstanceInstrumentation.class.getName();
        } else {
            final int idx = defineClass.indexOf(':');
            if (idx != -1) {
                if (defineClass.length() > idx) {
                    arg = defineClass.substring(idx + 1);
                }
                defineClass = defineClass.substring(0,idx);
            }
        }
        return new PluginDefine(pluginName, defineClass,arg);
    }

    public String getDefineClass() {
        return defineClass;
    }
}


