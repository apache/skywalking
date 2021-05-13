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

package org.apache.skywalking.apm.commons.datacarrier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * ISSUE-3064
 */
@BenchmarkMode({Mode.Throughput})
public class LinkedArrayBenchmark {

    @Benchmark
    public void testArrayCap1000() {
        ArrayList<SampleData> list = new ArrayList<SampleData>();
        for (int i = 0; i < 1000; i++) {
            list.add(new SampleData());
        }
    }

    @Benchmark
    public void testLinkedCap1000() {
        LinkedList<SampleData> list = new LinkedList<SampleData>();
        for (int i = 0; i < 1000; i++) {
            list.add(new SampleData());
        }
    }

    @Benchmark
    public void testArrayCap40000() {
        ArrayList<SampleData> list = new ArrayList<SampleData>();
        for (int i = 0; i < 40000; i++) {
            list.add(new SampleData());
        }
    }

    @Benchmark
    public void testLinkedCap40000() {
        LinkedList<SampleData> list = new LinkedList<SampleData>();
        for (int i = 0; i < 40000; i++) {
            list.add(new SampleData());
        }
    }

    @Benchmark
    public void testArrayStart1() {
        List<SampleData> consumerList = new ArrayList<SampleData>(1);
        for (int pos = 0; pos < 40000; pos++) {
            consumerList.add(new SampleData());
        }
    }

    @Benchmark
    public void testArrayStart10() {
        List<SampleData> consumerList = new ArrayList<SampleData>(10);
        for (int pos = 0; pos < 40000; pos++) {
            consumerList.add(new SampleData());
        }
    }

    @Benchmark
    public void testArrayStart8000() {
        List<SampleData> consumerList = new ArrayList<SampleData>(8000);
        for (int pos = 0; pos < 40000; pos++) {
            consumerList.add(new SampleData());
        }
    }

    @Benchmark
    public void testArrayStart40000() {
        List<SampleData> consumerList = new ArrayList<SampleData>(40000);
        for (int pos = 0; pos < 40000; pos++) {
            consumerList.add(new SampleData());
        }
    }

    @Benchmark
    public void testReusedArray() {
        List<SampleData> consumerList = new ArrayList<SampleData>();
        for (int times = 0; times < 1000; times++) {
            for (int pos = 0; pos < 40000; pos++) {
                consumerList.add(new SampleData());
            }
            consumerList.clear();
        }
    }

    @Benchmark
    public void testLinked() {
        for (int times = 0; times < 1000; times++) {
            List<SampleData> consumerList = new LinkedList<SampleData>();

            for (int pos = 0; pos < 40000; pos++) {
                consumerList.add(new SampleData());
            }
        }
    }

    @Benchmark
    public void testReusedLinked() {
        List<SampleData> consumerList = new LinkedList<SampleData>();
        for (int times = 0; times < 1000; times++) {

            for (int pos = 0; pos < 40000; pos++) {
                consumerList.add(new SampleData());
            }
            consumerList.clear();
        }
    }

    @Benchmark
    public void testArrayList200K() {
        ArrayList<SampleData> list = new ArrayList<SampleData>(4000);
        for (int times = 0; times < 1000; times++) {
            for (int pos = 0; pos < 200000; pos++) {
                list.add(new SampleData());
            }
            list.clear();
        }
    }

    @Benchmark
    public void testReusedLinked200K() {
        LinkedList<SampleData> list = new LinkedList<SampleData>();
        for (int times = 0; times < 1000; times++) {
            for (int pos = 0; pos < 200000; pos++) {
                list.add(new SampleData());
            }
            list.clear();
        }
    }

    @Benchmark
    public void testLinked200K() {
        for (int times = 0; times < 1000; times++) {
            LinkedList<SampleData> list = new LinkedList<SampleData>();
            for (int pos = 0; pos < 200000; pos++) {
                list.add(new SampleData());
            }
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(LinkedArrayBenchmark.class.getName())
                                          .addProfiler(GCProfiler.class)
                                          .jvmArgsAppend("-Xmx512m", "-Xms512m")
                                          .forks(1)
                                          .build();
        new Runner(opt).run();
    }
    /*
        Environment:

        # JMH version: 1.21
        # VM version: JDK 1.8.0_121, Java HotSpot(TM) 64-Bit Server VM, 25.121-b13
        # VM invoker: C:\Program Files\Java\jdk1.8.0_121\jre\bin\java.exe
        # VM options: -javaagent:C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2017.2.1\lib\idea_rt.jar=51557:C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2017.2.1\bin -Dfile.encoding=UTF-8 -Xmx512m -Xms512m
        # Warmup: 5 iterations, 10 s each
        # Measurement: 5 iterations, 10 s each
        # Timeout: 10 min per iteration
        # Threads: 1 thread, will synchronize iterations
        # Benchmark mode: Throughput, ops/time

        Benchmark                                                                    Mode  Cnt           Score          Error   Units
        LinkedArrayBenchmark.testArrayCap1000                                       thrpt    5      143087.182 ±     3142.078   ops/s
        LinkedArrayBenchmark.testArrayCap1000:·gc.alloc.rate                        thrpt    5        5067.966 ±      111.247  MB/sec
        LinkedArrayBenchmark.testArrayCap1000:·gc.alloc.rate.norm                   thrpt    5       39000.000 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayCap1000:·gc.churn.PS_Eden_Space               thrpt    5        5067.921 ±       98.198  MB/sec
        LinkedArrayBenchmark.testArrayCap1000:·gc.churn.PS_Eden_Space.norm          thrpt    5       38999.800 ±      214.267    B/op
        LinkedArrayBenchmark.testArrayCap1000:·gc.churn.PS_Survivor_Space           thrpt    5           0.649 ±        0.200  MB/sec
        LinkedArrayBenchmark.testArrayCap1000:·gc.churn.PS_Survivor_Space.norm      thrpt    5           4.993 ±        1.620    B/op
        LinkedArrayBenchmark.testArrayCap1000:·gc.count                             thrpt    5        1570.000                 counts
        LinkedArrayBenchmark.testArrayCap1000:·gc.time                              thrpt    5         701.000                     ms
        LinkedArrayBenchmark.testArrayCap40000                                      thrpt    5        3765.411 ±      194.475   ops/s
        LinkedArrayBenchmark.testArrayCap40000:·gc.alloc.rate                       thrpt    5        5230.501 ±      270.947  MB/sec
        LinkedArrayBenchmark.testArrayCap40000:·gc.alloc.rate.norm                  thrpt    5     1529496.011 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayCap40000:·gc.churn.PS_Eden_Space              thrpt    5        5243.183 ±      272.428  MB/sec
        LinkedArrayBenchmark.testArrayCap40000:·gc.churn.PS_Eden_Space.norm         thrpt    5     1533203.926 ±     3832.510    B/op
        LinkedArrayBenchmark.testArrayCap40000:·gc.churn.PS_Survivor_Space          thrpt    5           6.820 ±        2.362  MB/sec
        LinkedArrayBenchmark.testArrayCap40000:·gc.churn.PS_Survivor_Space.norm     thrpt    5        1994.409 ±      698.911    B/op
        LinkedArrayBenchmark.testArrayCap40000:·gc.count                            thrpt    5        1646.000                 counts
        LinkedArrayBenchmark.testArrayCap40000:·gc.time                             thrpt    5        1280.000                     ms
        LinkedArrayBenchmark.testArrayList200K                                      thrpt    5           0.664 ±        0.050   ops/s
        LinkedArrayBenchmark.testArrayList200K:·gc.alloc.rate                       thrpt    5        2903.182 ±      210.494  MB/sec
        LinkedArrayBenchmark.testArrayList200K:·gc.alloc.rate.norm                  thrpt    5  4802736157.714 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayList200K:·gc.churn.PS_Eden_Space              thrpt    5        2901.983 ±      222.656  MB/sec
        LinkedArrayBenchmark.testArrayList200K:·gc.churn.PS_Eden_Space.norm         thrpt    5  4800680520.914 ± 39561672.824    B/op
        LinkedArrayBenchmark.testArrayList200K:·gc.churn.PS_Survivor_Space          thrpt    5          19.788 ±        2.228  MB/sec
        LinkedArrayBenchmark.testArrayList200K:·gc.churn.PS_Survivor_Space.norm     thrpt    5    32731369.371 ±  1782913.951    B/op
        LinkedArrayBenchmark.testArrayList200K:·gc.count                            thrpt    5        1012.000                 counts
        LinkedArrayBenchmark.testArrayList200K:·gc.time                             thrpt    5        9026.000                     ms
        LinkedArrayBenchmark.testArrayStart1                                        thrpt    5        3036.206 ±      146.907   ops/s
        LinkedArrayBenchmark.testArrayStart1:·gc.alloc.rate                         thrpt    5        4004.134 ±      193.620  MB/sec
        LinkedArrayBenchmark.testArrayStart1:·gc.alloc.rate.norm                    thrpt    5     1452104.014 ±        0.002    B/op
        LinkedArrayBenchmark.testArrayStart1:·gc.churn.PS_Eden_Space                thrpt    5        4010.593 ±      201.502  MB/sec
        LinkedArrayBenchmark.testArrayStart1:·gc.churn.PS_Eden_Space.norm           thrpt    5     1454441.827 ±    12106.958    B/op
        LinkedArrayBenchmark.testArrayStart1:·gc.churn.PS_Survivor_Space            thrpt    5           4.471 ±        1.039  MB/sec
        LinkedArrayBenchmark.testArrayStart1:·gc.churn.PS_Survivor_Space.norm       thrpt    5        1621.402 ±      380.693    B/op
        LinkedArrayBenchmark.testArrayStart1:·gc.count                              thrpt    5        1260.000                 counts
        LinkedArrayBenchmark.testArrayStart1:·gc.time                               thrpt    5         946.000                     ms
        LinkedArrayBenchmark.testArrayStart10                                       thrpt    5        3953.451 ±      124.425   ops/s
        LinkedArrayBenchmark.testArrayStart10:·gc.alloc.rate                        thrpt    5        5491.766 ±      172.901  MB/sec
        LinkedArrayBenchmark.testArrayStart10:·gc.alloc.rate.norm                   thrpt    5     1529496.011 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayStart10:·gc.churn.PS_Eden_Space               thrpt    5        5506.896 ±      179.841  MB/sec
        LinkedArrayBenchmark.testArrayStart10:·gc.churn.PS_Eden_Space.norm          thrpt    5     1533707.327 ±     6558.467    B/op
        LinkedArrayBenchmark.testArrayStart10:·gc.churn.PS_Survivor_Space           thrpt    5           7.319 ±        1.779  MB/sec
        LinkedArrayBenchmark.testArrayStart10:·gc.churn.PS_Survivor_Space.norm      thrpt    5        2038.423 ±      504.768    B/op
        LinkedArrayBenchmark.testArrayStart10:·gc.count                             thrpt    5        1728.000                 counts
        LinkedArrayBenchmark.testArrayStart10:·gc.time                              thrpt    5        1350.000                     ms
        LinkedArrayBenchmark.testArrayStart40000                                    thrpt    5        3445.048 ±       38.938   ops/s
        LinkedArrayBenchmark.testArrayStart40000:·gc.alloc.rate                     thrpt    5        3504.290 ±       39.160  MB/sec
        LinkedArrayBenchmark.testArrayStart40000:·gc.alloc.rate.norm                thrpt    5     1120016.013 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayStart40000:·gc.churn.PS_Eden_Space            thrpt    5        3506.791 ±       62.456  MB/sec
        LinkedArrayBenchmark.testArrayStart40000:·gc.churn.PS_Eden_Space.norm       thrpt    5     1120811.902 ±    10367.121    B/op
        LinkedArrayBenchmark.testArrayStart40000:·gc.churn.PS_Survivor_Space        thrpt    5           4.731 ±        0.275  MB/sec
        LinkedArrayBenchmark.testArrayStart40000:·gc.churn.PS_Survivor_Space.norm   thrpt    5        1512.123 ±       91.484    B/op
        LinkedArrayBenchmark.testArrayStart40000:·gc.count                          thrpt    5        1100.000                 counts
        LinkedArrayBenchmark.testArrayStart40000:·gc.time                           thrpt    5         805.000                     ms
        LinkedArrayBenchmark.testArrayStart8000                                     thrpt    5        2940.747 ±       32.257   ops/s
        LinkedArrayBenchmark.testArrayStart8000:·gc.alloc.rate                      thrpt    5        3691.430 ±       39.870  MB/sec
        LinkedArrayBenchmark.testArrayStart8000:·gc.alloc.rate.norm                 thrpt    5     1382080.015 ±        0.001    B/op
        LinkedArrayBenchmark.testArrayStart8000:·gc.churn.PS_Eden_Space             thrpt    5        3699.920 ±       46.996  MB/sec
        LinkedArrayBenchmark.testArrayStart8000:·gc.churn.PS_Eden_Space.norm        thrpt    5     1385258.364 ±     7458.176    B/op
        LinkedArrayBenchmark.testArrayStart8000:·gc.churn.PS_Survivor_Space         thrpt    5           3.228 ±        0.276  MB/sec
        LinkedArrayBenchmark.testArrayStart8000:·gc.churn.PS_Survivor_Space.norm    thrpt    5        1208.384 ±      102.584    B/op
        LinkedArrayBenchmark.testArrayStart8000:·gc.count                           thrpt    5        1160.000                 counts
        LinkedArrayBenchmark.testArrayStart8000:·gc.time                            thrpt    5         776.000                     ms
        LinkedArrayBenchmark.testLinked                                             thrpt    5           2.145 ±        0.023   ops/s
        LinkedArrayBenchmark.testLinked:·gc.alloc.rate                              thrpt    5        3744.537 ±       38.773  MB/sec
        LinkedArrayBenchmark.testLinked:·gc.alloc.rate.norm                         thrpt    5  1920000019.636 ±        0.001    B/op
        LinkedArrayBenchmark.testLinked:·gc.churn.PS_Eden_Space                     thrpt    5        3743.961 ±       36.688  MB/sec
        LinkedArrayBenchmark.testLinked:·gc.churn.PS_Eden_Space.norm                thrpt    5  1919709109.527 ± 17177042.598    B/op
        LinkedArrayBenchmark.testLinked:·gc.churn.PS_Survivor_Space                 thrpt    5           9.470 ±        0.430  MB/sec
        LinkedArrayBenchmark.testLinked:·gc.churn.PS_Survivor_Space.norm            thrpt    5     4855621.818 ±   264728.918    B/op
        LinkedArrayBenchmark.testLinked:·gc.count                                   thrpt    5        1217.000                 counts
        LinkedArrayBenchmark.testLinked:·gc.time                                    thrpt    5        3697.000                     ms
        LinkedArrayBenchmark.testLinked200K                                         thrpt    5           0.340 ±        0.013   ops/s
        LinkedArrayBenchmark.testLinked200K:·gc.alloc.rate                          thrpt    5        2989.665 ±      108.530  MB/sec
        LinkedArrayBenchmark.testLinked200K:·gc.alloc.rate.norm                     thrpt    5  9600000108.000 ±        0.001    B/op
        LinkedArrayBenchmark.testLinked200K:·gc.churn.PS_Eden_Space                 thrpt    5        2990.920 ±      116.103  MB/sec
        LinkedArrayBenchmark.testLinked200K:·gc.churn.PS_Eden_Space.norm            thrpt    5  9603986226.400 ± 39954550.430    B/op
        LinkedArrayBenchmark.testLinked200K:·gc.churn.PS_Survivor_Space             thrpt    5          32.536 ±        4.681  MB/sec
        LinkedArrayBenchmark.testLinked200K:·gc.churn.PS_Survivor_Space.norm        thrpt    5   104493875.200 ± 16889681.984    B/op
        LinkedArrayBenchmark.testLinked200K:·gc.count                               thrpt    5        1235.000                 counts
        LinkedArrayBenchmark.testLinked200K:·gc.time                                thrpt    5       15644.000                     ms
        LinkedArrayBenchmark.testLinkedCap1000                                      thrpt    5       84999.730 ±     1164.113   ops/s
        LinkedArrayBenchmark.testLinkedCap1000:·gc.alloc.rate                       thrpt    5        3705.698 ±       50.753  MB/sec
        LinkedArrayBenchmark.testLinkedCap1000:·gc.alloc.rate.norm                  thrpt    5       48000.001 ±        0.001    B/op
        LinkedArrayBenchmark.testLinkedCap1000:·gc.churn.PS_Eden_Space              thrpt    5        3705.991 ±       71.457  MB/sec
        LinkedArrayBenchmark.testLinkedCap1000:·gc.churn.PS_Eden_Space.norm         thrpt    5       48003.617 ±      320.127    B/op
        LinkedArrayBenchmark.testLinkedCap1000:·gc.churn.PS_Survivor_Space          thrpt    5           0.520 ±        0.154  MB/sec
        LinkedArrayBenchmark.testLinkedCap1000:·gc.churn.PS_Survivor_Space.norm     thrpt    5           6.739 ±        2.066    B/op
        LinkedArrayBenchmark.testLinkedCap1000:·gc.count                            thrpt    5        1148.000                 counts
        LinkedArrayBenchmark.testLinkedCap1000:·gc.time                             thrpt    5         515.000                     ms
        LinkedArrayBenchmark.testLinkedCap40000                                     thrpt    5        2001.889 ±       58.692   ops/s
        LinkedArrayBenchmark.testLinkedCap40000:·gc.alloc.rate                      thrpt    5        3490.899 ±      102.356  MB/sec
        LinkedArrayBenchmark.testLinkedCap40000:·gc.alloc.rate.norm                 thrpt    5     1920000.022 ±        0.001    B/op
        LinkedArrayBenchmark.testLinkedCap40000:·gc.churn.PS_Eden_Space             thrpt    5        3491.448 ±      111.952  MB/sec
        LinkedArrayBenchmark.testLinkedCap40000:·gc.churn.PS_Eden_Space.norm        thrpt    5     1920296.231 ±    15332.688    B/op
        LinkedArrayBenchmark.testLinkedCap40000:·gc.churn.PS_Survivor_Space         thrpt    5           8.708 ±        0.925  MB/sec
        LinkedArrayBenchmark.testLinkedCap40000:·gc.churn.PS_Survivor_Space.norm    thrpt    5        4788.927 ±      381.943    B/op
        LinkedArrayBenchmark.testLinkedCap40000:·gc.count                           thrpt    5        1108.000                 counts
        LinkedArrayBenchmark.testLinkedCap40000:·gc.time                            thrpt    5        3444.000                     ms
        LinkedArrayBenchmark.testReusedArray                                        thrpt    5           3.128 ±        0.254   ops/s
        LinkedArrayBenchmark.testReusedArray:·gc.alloc.rate                         thrpt    5        2731.835 ±      222.486  MB/sec
        LinkedArrayBenchmark.testReusedArray:·gc.alloc.rate.norm                    thrpt    5   960569533.505 ±        1.150    B/op
        LinkedArrayBenchmark.testReusedArray:·gc.churn.PS_Eden_Space                thrpt    5        2730.828 ±      245.487  MB/sec
        LinkedArrayBenchmark.testReusedArray:·gc.churn.PS_Eden_Space.norm           thrpt    5   960179747.003 ±  8148505.430    B/op
        LinkedArrayBenchmark.testReusedArray:·gc.churn.PS_Survivor_Space            thrpt    5           1.864 ±        0.329  MB/sec
        LinkedArrayBenchmark.testReusedArray:·gc.churn.PS_Survivor_Space.norm       thrpt    5      656036.904 ±   159198.847    B/op
        LinkedArrayBenchmark.testReusedArray:·gc.count                              thrpt    5         875.000                 counts
        LinkedArrayBenchmark.testReusedArray:·gc.time                               thrpt    5        2103.000                     ms
        LinkedArrayBenchmark.testReusedLinked                                       thrpt    5           1.574 ±        0.015   ops/s
        LinkedArrayBenchmark.testReusedLinked:·gc.alloc.rate                        thrpt    5        2746.313 ±       25.513  MB/sec
        LinkedArrayBenchmark.testReusedLinked:·gc.alloc.rate.norm                   thrpt    5  1920000059.800 ±        4.218    B/op
        LinkedArrayBenchmark.testReusedLinked:·gc.churn.PS_Eden_Space               thrpt    5        2745.434 ±       25.184  MB/sec
        LinkedArrayBenchmark.testReusedLinked:·gc.churn.PS_Eden_Space.norm          thrpt    5  1919385600.000 ±   836969.504    B/op
        LinkedArrayBenchmark.testReusedLinked:·gc.churn.PS_Survivor_Space           thrpt    5           6.834 ±        0.638  MB/sec
        LinkedArrayBenchmark.testReusedLinked:·gc.churn.PS_Survivor_Space.norm      thrpt    5     4777984.000 ±   456748.586    B/op
        LinkedArrayBenchmark.testReusedLinked:·gc.count                             thrpt    5         886.000                 counts
        LinkedArrayBenchmark.testReusedLinked:·gc.time                              thrpt    5        3041.000                     ms
        LinkedArrayBenchmark.testReusedLinked200K                                   thrpt    5           0.253 ±        0.009   ops/s
        LinkedArrayBenchmark.testReusedLinked200K:·gc.alloc.rate                    thrpt    5        2226.639 ±       75.414  MB/sec
        LinkedArrayBenchmark.testReusedLinked200K:·gc.alloc.rate.norm               thrpt    5  9600000178.133 ±       18.369    B/op
        LinkedArrayBenchmark.testReusedLinked200K:·gc.churn.PS_Eden_Space           thrpt    5        2225.749 ±       77.891  MB/sec
        LinkedArrayBenchmark.testReusedLinked200K:·gc.churn.PS_Eden_Space.norm      thrpt    5  9596148100.800 ± 50593440.713    B/op
        LinkedArrayBenchmark.testReusedLinked200K:·gc.churn.PS_Survivor_Space       thrpt    5          22.781 ±        4.309  MB/sec
        LinkedArrayBenchmark.testReusedLinked200K:·gc.churn.PS_Survivor_Space.norm  thrpt    5    98238464.000 ± 19995139.006    B/op
        LinkedArrayBenchmark.testReusedLinked200K:·gc.count                         thrpt    5         930.000                 counts
        LinkedArrayBenchmark.testReusedLinked200K:·gc.time                          thrpt    5       12241.000                     ms
     */
}
