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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
class RetryableTestTest {
    private static final AtomicInteger TIMES1 = new AtomicInteger(1);
    private static final AtomicInteger TIMES2 = new AtomicInteger(1);
    private static final AtomicInteger TIMES3 = new AtomicInteger(1);

    @Timeout(30)
    @RetryableTest
    @DisplayName("should retry on failure")
    void shouldRetryOnFailure() {
        if (TIMES1.getAndIncrement() == 1) {
            Integer.parseInt("abc");
        }
    }

    @Timeout(30)
    @RetryableTest(3)
    @DisplayName("should retry specific times")
    void shouldRetrySpecificTimes() {
        if (TIMES3.getAndIncrement() < 3) {
            Integer.parseInt("abc");
        }
    }

    @Timeout(30)
    @DisplayName("should retry specific exception")
    @RetryableTest(throwable = NumberFormatException.class)
    void shouldRetrySpecificException() {
        final int retriedTimes = TIMES2.getAndIncrement();
        if (retriedTimes == 1) {
            throw new NumberFormatException("not NumberFormatException");
        }
        if (retriedTimes > 2) {
            fail("should never happen");
        }
    }
}
