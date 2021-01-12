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

package org.apache.skywalking.e2e.retryable;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

import static org.apache.skywalking.e2e.retryable.RetryableTestExtension.TEST_SHOULD_RETRY;

@RequiredArgsConstructor
final class OneShotExtension
    implements ExecutionCondition, AfterTestExecutionCallback, TestExecutionExceptionHandler {

    private final Class<? extends Throwable> throwableClass;
    private final int invocation;
    private final int times;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        final boolean shouldRetry = Stores.get(context, TEST_SHOULD_RETRY, Boolean.class) != Boolean.FALSE;

        if (!shouldRetry) {
            return ConditionEvaluationResult.disabled("test passed after " + invocation + " attempts");
        }

        Stores.put(context, TEST_SHOULD_RETRY, invocation < times || retryInfinitely());
        return ConditionEvaluationResult.enabled("test is retried " + times + " times and is still failed");
    }

    @Override
    public void afterTestExecution(final ExtensionContext context) {
        final Optional<Throwable> throwable = context.getExecutionException();
        Stores.put(
            context, TEST_SHOULD_RETRY,
            throwable.isPresent() && throwable.get().getClass() == TestAbortedException.class
        );
    }

    @Override
    public void handleTestExecutionException(final ExtensionContext context,
                                             final Throwable throwable) throws Throwable {
        if (Stores.get(context, TEST_SHOULD_RETRY, Boolean.class) == Boolean.FALSE) {
            throw throwable;
        }

        if (retryOnAnyException()) {
            throw new TestAbortedException("test failed, will retry", throwable);
        }

        if (throwableClass == throwable.getClass()) {
            throw new TestAbortedException("test failed, will retry", throwable);
        }

        throw throwable;
    }

    private boolean retryOnAnyException() {
        return throwableClass == Throwable.class;
    }

    private boolean retryInfinitely() {
        return times < 0;
    }
}
