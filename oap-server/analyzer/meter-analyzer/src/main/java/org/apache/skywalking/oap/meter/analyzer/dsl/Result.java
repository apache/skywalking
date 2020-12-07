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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Result indicates the parsing result of expression.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
@Getter
public class Result {

    /**
     * fail is a static factory method builds failed result based on {@link Throwable}.
     *
     * @param throwable to build failed result.
     * @return failed result.
     */
    public static Result fail(final Throwable throwable) {
        return new Result(false, true, throwable.getMessage(), SampleFamily.EMPTY);
    }

    /**
     * fail is a static factory method builds failed result based on error message.
     *
     * @param message is the error details why the result is failed.
     * @return failed result.
     */
    public static Result fail(String message) {
        return new Result(false, false, message, SampleFamily.EMPTY);
    }

    /**
     * fail is a static factory method builds failed result.
     *
     * @return failed result.
     */
    public static Result fail() {
        return new Result(false, false, null, SampleFamily.EMPTY);
    }

    /**
     * success is a static factory method builds successful result.
     *
     * @param sf is the parsed result.
     * @return successful result.
     */
    public static Result success(SampleFamily sf) {
        return new Result(true, false, null, sf);
    }

    private final boolean success;

    private final boolean isThrowable;

    private final String error;

    private final SampleFamily data;
}
