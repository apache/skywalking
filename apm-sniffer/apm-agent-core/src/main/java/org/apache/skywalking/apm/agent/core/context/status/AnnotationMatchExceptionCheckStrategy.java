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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class AnnotationMatchExceptionCheckStrategy implements ExceptionCheckStrategy {

    private final Set<Class<? extends Throwable>> ignoredExceptions = new CopyOnWriteArraySet<>();

    @Override
    public boolean isError(final Throwable e) {
        Class<? extends Throwable> clazz = e.getClass();
        if (ignoredExceptions.contains(clazz)) {
            return false;
        }
        try {
            String value = (String) clazz.getMethod("getSkyWalkingDynamicField").invoke(e);
            if (ExceptionCheckStrategy.class.getSimpleName().equals(value)) {
                ignoredExceptions.add(e.getClass());
                return false;
            }
        } catch (Exception ignore) {
        }
        return true;
    }
}