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

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tests annotated with {@link RetryableTest @RetryableTest} can be retried automatically on failure.
 */
@Inherited
@Documented
@TestTemplate
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@ExtendWith(RetryableTestExtension.class)
public @interface RetryableTest {
    /**
     * @return the {@link Throwable} class, when this type of throwable is thrown, the test should be retried; if {@link
     * Throwable Throwable.class} is specified, the failed test will be retried when any exception is thrown. {@code
     * Throwable.class} by default
     */
    Class<? extends Throwable> throwable() default Throwable.class;

    /**
     * @return maximum times to retry, or -1 for infinite retries. {@code -1} by default.
     */
    int value() default 60;

    /**
     * @return the interval between any two retries, in millisecond. {@code 1000} by default.
     */
    long interval() default 10000;
}
