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

package org.apache.skywalking.oap.server.core.storage;

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
        PersistenceTimer.DefaultBlockingBatchQueue blockingBatchQueueWithSynchronized = new PersistenceTimer.DefaultBlockingBatchQueue(
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
                    queue.offer(myState.willAdd);
                }
                latch.countDown();
                return null;
            });
        }

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < myState.consumerCount; i++) {
            Future<?> submit = myState.consumer.submit(() -> {
                while (!queue.poll().isEmpty()) {
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
            .forks(2)
            .build();
        new Runner(opt).run();
    }

    /**
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_172, Java HotSpot(TM) 64-Bit Server VM, 25.172-b11
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_172.jdk/Contents/Home/jre/bin/java
     * # VM options: -javaagent:/Users/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.7442.40/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=50386:/Users/alvin/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.7442.40/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     * # Warmup: 5 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 1 thread, will synchronize iterations
     * # Benchmark mode: Throughput, ops/time
     *
     *  Benchmark                                             Mode  Cnt   Score   Error  Units
     *  BlockingBatchQueueBenchmark.testLinkedBlockingQueue  thrpt   10   0.317 ± 0.032  ops/s
     *  BlockingBatchQueueBenchmark.testReentrantLock        thrpt   10  16.018 ± 1.553  ops/s
     *  BlockingBatchQueueBenchmark.testSynchronized         thrpt   10  16.769 ± 0.533  ops/s
     *
     */

}
