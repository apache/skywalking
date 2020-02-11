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

package org.apache.skywalking.apm.plugin;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark                     Mode  Cnt     Score     Error   Units ArbitrarySetTest.array       thrpt   10  2360.500
 * ± 138.279  ops/ms ArbitrarySetTest.arrayList   thrpt   10  1080.005 ± 225.897  ops/ms ArbitrarySetTest.linkedList
 * thrpt   10   188.007 ±  11.739  ops/ms ArbitrarySetTest.treeMap     thrpt   10   214.384 ±  27.816  ops/ms
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(2)
@Warmup(iterations = 4)
@Measurement(iterations = 5)
public class ArbitrarySetTest {
    private static final Object PLACEHOLDER = new Object();

    @Benchmark
    public void arrayList() {
        ArrayList<Object> list = new ArrayList<Object>(Collections.nCopies(20, PLACEHOLDER));
        for (int i = 0; i < 100; i++) {
            int oldSize = list.size();
            if (i >= oldSize) {
                int newSize = Math.max(oldSize * 2, i);
                list.addAll(oldSize, Collections.nCopies(newSize - oldSize, PLACEHOLDER));
            }
            list.set(i, i);
        }
    }

    @Benchmark
    public void linkedList() {
        LinkedList<Object> list = new LinkedList<Object>(Collections.nCopies(20, PLACEHOLDER));
        for (int i = 0; i < 100; i++) {
            int oldSize = list.size();
            if (i >= oldSize) {
                int newSize = Math.max(oldSize * 2, i);
                list.addAll(oldSize, Collections.nCopies(newSize - oldSize, PLACEHOLDER));
            }
            list.set(i, i);
        }
    }

    @Benchmark
    public void array() {
        Object[] array = new Object[20];
        Arrays.fill(array, PLACEHOLDER);
        for (int i = 1; i <= 100; i++) {
            int length = array.length;
            if (i >= length) {
                int newSize = Math.max(i, length * 2);
                Object[] newArray = new Object[newSize];
                System.arraycopy(array, 0, newArray, 0, length);
                Arrays.fill(newArray, length, newSize, PLACEHOLDER);
                array = newArray;
            }
            array[i] = i;
        }
    }

    @Benchmark
    public void treeMap() {
        final Map<Integer, Object> treeMap = new TreeMap<Integer, Object>();
        for (int i = 0; i < 100; i++) {
            treeMap.put(i, i);
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(ArbitrarySetTest.class.getSimpleName()).build();
        new Runner(options).run();
    }
}
