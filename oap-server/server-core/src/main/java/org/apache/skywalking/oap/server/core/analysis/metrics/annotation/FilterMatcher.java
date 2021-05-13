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

package org.apache.skywalking.oap.server.core.analysis.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanMatch;

/**
 * Classes annotated with {@code FilterMatcher} are processors of the expressions in {@code filter} of the OAL script.
 * Take {@link BooleanMatch} as an example.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterMatcher {
    /**
     * @return the operator name(s) defined in the .g4 files, such as {@code lessEqualMatch} and {@code notEqualMatch},
     * the default value is the name of the class annotated with {@link FilterMatcher}, with the first letter being
     * lowercase.
     */
    String[] value() default {};
}
