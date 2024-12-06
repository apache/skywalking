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

package org.apache.skywalking.oap.query.graphql;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

/**
 * The utility class for async GraphQL query.
 * All the async GraphQL query should be wrapped by this class and shared the same executor.
 */
@Slf4j
public class AsyncQueryUtils {
    private static final Executor EXECUTOR = new ForkJoinPool(
        Runtime.getRuntime().availableProcessors(), defaultForkJoinWorkerThreadFactory, null, true);

    public static <U> CompletableFuture<U> queryAsync(Callable<U> caller) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return caller.call();
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }
}
