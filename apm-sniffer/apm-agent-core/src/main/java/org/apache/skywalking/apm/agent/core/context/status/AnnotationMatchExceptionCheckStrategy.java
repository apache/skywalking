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

package org.apache.skywalking.apm.agent.core.context.status;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * AnnotationMatchExceptionCheckStrategy does an annotation matching check for a traced exception. If it has been
 * annotated with org.apache.skywalking.apm.toolkit.trace.IgnoredException, the error status of the span wouldn't be
 * changed. Because of the annotation supports integration, the subclasses would be also annotated with it.
 */
public class AnnotationMatchExceptionCheckStrategy implements ExceptionCheckStrategy {

    private static final String TAG_NAME = AnnotationMatchExceptionCheckStrategy.class.getSimpleName();

    @Override
    public boolean isError(final Throwable e) {
        return !(e instanceof EnhancedInstance) || !TAG_NAME.equals(((EnhancedInstance) e).getSkyWalkingDynamicField());
    }
}