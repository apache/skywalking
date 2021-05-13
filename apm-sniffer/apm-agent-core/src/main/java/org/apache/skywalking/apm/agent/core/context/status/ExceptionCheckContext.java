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
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExceptionCheckContext contains the exceptions that have been checked by the exceptionCheckStrategies.
 */
public enum ExceptionCheckContext {
    INSTANCE;

    private final Set<Class<? extends Throwable>> ignoredExceptions = ConcurrentHashMap.newKeySet(32);
    private final Set<Class<? extends Throwable>> errorStatusExceptions = ConcurrentHashMap.newKeySet(128);

    public boolean isChecked(Throwable throwable) {
        return ignoredExceptions.contains(throwable.getClass()) || errorStatusExceptions.contains(throwable.getClass());
    }

    public boolean isError(Throwable throwable) {
        Class<? extends Throwable> clazz = throwable.getClass();
        return errorStatusExceptions.contains(clazz) || (!ignoredExceptions.contains(clazz));
    }

    public void registerIgnoredException(Throwable throwable) {
        ignoredExceptions.add(throwable.getClass());
    }

    public void registerErrorStatusException(Throwable throwable) {
        errorStatusExceptions.add(throwable.getClass());
    }

}
