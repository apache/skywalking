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
 */

package org.apache.skywalking.oap.meter.analyzer.dsl.counter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.vavr.Tuple2;
import java.time.Duration;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

import static java.time.Instant.parse;

public class CounterWindowTest {

    public static List<Tuple2<Long, Double>> parameters() {
        return Lists.newArrayList(
            new Tuple2<>(parse("2020-09-11T11:11:01.03Z").toEpochMilli(), 10d),
            new Tuple2<>(parse("2020-09-11T11:11:15.99Z").toEpochMilli(), 11d),
            new Tuple2<>(parse("2020-09-11T11:11:31.00Z").toEpochMilli(), 12d),
            new Tuple2<>(parse("2020-09-11T11:11:46.09Z").toEpochMilli(), 13d),
            new Tuple2<>(parse("2020-09-11T11:12:00.97Z").toEpochMilli(), 14d),
            new Tuple2<>(parse("2020-09-11T11:11:00.97Z").toEpochMilli(), 15d),
            new Tuple2<>(parse("2020-09-11T11:12:16.60Z").toEpochMilli(), 16d),
            new Tuple2<>(parse("2020-09-11T11:12:31.66Z").toEpochMilli(), 17d)
        );
    }

    @Test
    public void testPT15S() {
        double[] actuals = parameters().stream().mapToDouble(e -> {
            Tuple2<Long, Double> increase = CounterWindow.INSTANCE.increase(
                "test", ImmutableMap.<String, String>builder().build(), e._2,
                Duration.parse("PT15S").getSeconds() * 1000, e._1
            );
            return e._2 - increase._2;
        }).toArray();

        Assert.assertArrayEquals(new double[] {0, 1d, 1d, 1d, 1d, 0d, 2d, 1d}, actuals, 0.d);
    }

    @Test
    public void testPT35S() {
        double[] actuals = parameters().stream().mapToDouble(e -> {
            Tuple2<Long, Double> increase = CounterWindow.INSTANCE.increase(
                "test", ImmutableMap.<String, String>builder().build(), e._2,
                Duration.parse("PT35S").getSeconds() * 1000, e._1
            );
            return e._2 - increase._2;
        }).toArray();

        Assert.assertArrayEquals(new double[] {0, 1d, 2d, 2d, 2d, 0d, 3d, 3d}, actuals, 0.d);
    }

    @Test
    public void testPT1M() {
        double[] actuals = parameters().stream().mapToDouble(e -> {
            Tuple2<Long, Double> increase = CounterWindow.INSTANCE.increase(
                "test", ImmutableMap.<String, String>builder().build(), e._2,
                Duration.parse("PT1M").getSeconds() * 1000, e._1
            );
            return e._2 - increase._2;
        }).toArray();

        Assert.assertArrayEquals(new double[] {0, 1d, 2d, 3d, 4d, 0d, 5d, 5d}, actuals, 0.d);
    }

    @Test
    public void testPT2M() {
        double[] actuals = parameters().stream().mapToDouble(e -> {
            Tuple2<Long, Double> increase = CounterWindow.INSTANCE.increase(
                "test", ImmutableMap.<String, String>builder().build(), e._2,
                Duration.parse("PT2M").getSeconds() * 1000, e._1
            );
            return e._2 - increase._2;
        }).toArray();

        Assert.assertArrayEquals(new double[] {0, 1d, 2d, 3d, 4d, 0d, 1d, 2d}, actuals, 0.d);
    }
}
