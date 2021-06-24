/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.apache.skywalking.oap.server.core.storage.jmh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Fork(2)
public class BlockingBatchQueueBenchmark {

    @State(Scope.Benchmark)
    public static class MyState {

        int count = 10_000_000;
        BlockingBatchQueueWithSynchronized blockingBatchQueueWithSynchronized = new BlockingBatchQueueWithSynchronized(
            50000);
        BlockingBatchQueueWithLinkedBlockingQueue blockingBatchQueueWithLinkedBlockingQueue = new BlockingBatchQueueWithLinkedBlockingQueue(
            50000);
        BlockingBatchQueueWithReentrantLock blockingBatchQueueWithReentrantLock = new BlockingBatchQueueWithReentrantLock(
            50000);
        List<Integer> willAdd = IntStream.range(0, 1000).boxed().collect(Collectors.toList());
        int producerCount = 10;
        int consumerCount = 2;
        int producerLength = count / producerCount / 1000;

        ExecutorService producer;
        ExecutorService consumer;

        @Setup(Level.Invocation)
        public void before() {
            producer = Executors.newFixedThreadPool(producerCount);
            consumer = Executors.newFixedThreadPool(consumerCount);
        }

        @TearDown(Level.Invocation)
        public void after() {
            producer.shutdown();
            consumer.shutdown();
        }

    }

    @Benchmark
    public void testSynchronized(MyState myState) throws InterruptedException, ExecutionException {
        testProductAndConsume(myState, myState.blockingBatchQueueWithSynchronized);
    }

    @Benchmark
    public void testReentrantLock(MyState myState) throws InterruptedException, ExecutionException {
        testProductAndConsume(myState, myState.blockingBatchQueueWithReentrantLock);
    }

    @Benchmark
    public void testLinkedBlockingQueue(MyState myState) throws InterruptedException, ExecutionException {
        testProductAndConsume(myState, myState.blockingBatchQueueWithLinkedBlockingQueue);
    }

    private void testProductAndConsume(final MyState myState,
                                       BlockingBatchQueue queue) throws InterruptedException, ExecutionException {
        queue.furtherAppending();
        CountDownLatch latch = new CountDownLatch(myState.producerCount);
        for (int i = 0; i < myState.producerCount; i++) {
            myState.producer.submit(() -> {
                for (int j = 0; j < myState.producerLength; j++) {
                    queue.putMany(myState.willAdd);
                }
                latch.countDown();
                return null;
            });
        }

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < myState.consumerCount; i++) {
            Future<?> submit = myState.consumer.submit(() -> {
                while (!queue.popMany().isEmpty()) {
                }
                return null;
            });
            futures.add(submit);
        }

        latch.await();
        queue.noFurtherAppending();
        for (Future<?> future : futures) {
            future.get();
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(BlockingBatchQueueBenchmark.class.getSimpleName())
            .forks(1)
            .build();
        new Runner(opt).run();
    }

}
