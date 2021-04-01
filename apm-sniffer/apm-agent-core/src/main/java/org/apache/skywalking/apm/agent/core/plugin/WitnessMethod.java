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

import lombok.Getter;
import lombok.ToString;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Witness Method for plugin activation
 */
@ToString
public class WitnessMethod {

    /**
     * the class or interface name where the witness method is declared.
     */
    @Getter
    private final String declaringClassName;

    /**
     * matcher to match the witness method
     */
    @Getter
    private final ElementMatcher<? super MethodDescription.InDefinedShape> elementMatcher;

    /**
     * if exclusiveMode is {@link java.lang.Boolean#TRUE}, we do not want the selected method exists.
     * But in either cases, the declaring class should exist.
     */
    @Getter
    private final boolean exclusiveMode;

    /**
     * Shorthand constructor for WitnessMethod without breaking existing methods.
     * By default, the witness method works in the inclusive mode
     *
     * @param declaringClassName the class to find the specific method
     * @param elementMatcher     element matcher used to filter the declared methods in the class
     */
    public WitnessMethod(String declaringClassName, ElementMatcher<? super MethodDescription.InDefinedShape> elementMatcher) {
        this(declaringClassName, elementMatcher, false);
    }

    public WitnessMethod(String declaringClassName, ElementMatcher<? super MethodDescription.InDefinedShape> elementMatcher, boolean exclusiveMode) {
        this.declaringClassName = declaringClassName;
        this.elementMatcher = elementMatcher;
        this.exclusiveMode = exclusiveMode;
    }
}
