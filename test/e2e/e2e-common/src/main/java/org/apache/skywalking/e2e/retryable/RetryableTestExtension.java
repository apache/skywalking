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

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

final class RetryableTestExtension implements TestTemplateInvocationContextProvider {
    public static final String TEST_SHOULD_RETRY = "TEST_PASSED";

    @Override
    public boolean supportsTestTemplate(final ExtensionContext context) {
        return context.getTestMethod().map(m -> m.isAnnotationPresent(RetryableTest.class)).orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(final ExtensionContext context) {
        final RetryableTest retryableTest = context.getRequiredTestMethod().getAnnotation(RetryableTest.class);
        final Class<? extends Throwable> throwable = retryableTest.throwable();
        final long interval = retryableTest.interval();
        final int times = retryableTest.value();

        if (interval <= 0) {
            throw new IllegalArgumentException(
                "RetryableTest#interval must be a positive integer, but was " + interval
            );
        }

        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(new Iterator<Integer>() {
                final AtomicInteger count = new AtomicInteger(0);

                @Override
                @SneakyThrows
                public boolean hasNext() {
                    final boolean shouldRetry = Stores.get(context, TEST_SHOULD_RETRY, Boolean.class) != Boolean.FALSE;

                    final boolean hasNext = (times < 0 || count.get() <= times) && shouldRetry;

                    if (hasNext) {
                        Thread.sleep(interval);
                    }

                    return hasNext;
                }

                @Override
                public Integer next() {
                    return count.getAndIncrement();
                }
            }, Spliterator.NONNULL), false
        ).map(time -> new RetryableTestContext(throwable, time, times));
    }
}
