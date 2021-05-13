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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.apache.skywalking.apm.agent.core.conf.Config;

@AllArgsConstructor
public enum StatusChecker {

    /**
     * All exceptions would make the span tagged as the error status.
     */
    OFF(
        Collections.singletonList(new OffExceptionCheckStrategy()),
        (isError, throwable) -> {
        }
    ),

    /**
     * Hierarchy check the status of the traced exception.
     *
     * @see HierarchyMatchExceptionCheckStrategy
     * @see AnnotationMatchExceptionCheckStrategy
     */
    HIERARCHY_MATCH(
        Arrays.asList(
            new HierarchyMatchExceptionCheckStrategy(),
            new AnnotationMatchExceptionCheckStrategy()
        ),
        (isError, throwable) -> {
            if (isError) {
                ExceptionCheckContext.INSTANCE.registerErrorStatusException(throwable);
            } else {
                ExceptionCheckContext.INSTANCE.registerIgnoredException(throwable);
            }
        }
    );

    private final List<ExceptionCheckStrategy> strategies;

    private final ExceptionCheckCallback callback;

    public boolean checkStatus(Throwable e) {
        int maxDepth = Config.StatusCheck.MAX_RECURSIVE_DEPTH;
        boolean isError = true;
        while (isError && Objects.nonNull(e) && maxDepth-- > 0) {
            isError = check(e);
            e = e.getCause();
        }
        return isError;
    }

    private boolean check(final Throwable e) {
        boolean isError = ExceptionCheckContext.INSTANCE.isChecked(e)
            ? ExceptionCheckContext.INSTANCE.isError(e)
            : strategies.stream().allMatch(item -> item.isError(e));
        callback.onChecked(isError, e);
        return isError;
    }

    /**
     * The callback function would be triggered after an exception is checked by StatusChecker.
     */
    @FunctionalInterface
    private interface ExceptionCheckCallback {
        void onChecked(Boolean isError, Throwable throwable);
    }

}
