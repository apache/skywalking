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

package org.apache.skywalking.oap.server.receiver.telegraf;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Getter;
import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.TelegrafServiceHandler;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafDatum;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JSONConvertTest {

    private TelegrafDatum datum1;

    @Before
    public void initData() {
        // The telegrafDatum
        datum1 = new TelegrafDatum();

        Map<String, Object> fields1 = new HashMap<>();
        fields1.put("available", 60);
        fields1.put("available_percent", 60);
        fields1.put("total", 100);
        fields1.put("used", 40);
        fields1.put("used_percent", 40);

        Map<String, String> tags1 = new HashMap<>();
        tags1.put("host", "localHost");

        datum1.setFields(fields1);
        datum1.setName("mem");
        datum1.setTags(tags1);
        datum1.setTimestamp(1663391);
    }


    /**
     * The convert function in the {@link TelegrafServiceHandler}
     **/
    @Ignore
    public List<Sample> convertTelegraf(TelegrafDatum telegrafData) {

        List<Sample> sampleList = new ArrayList<>();

        Map<String, Object> fields = telegrafData.getFields();
        String name = telegrafData.getName();
        Map<String, String> tags = telegrafData.getTags();
        ImmutableMap<String, String> immutableTags = ImmutableMap.copyOf(tags);
        long timestamp = telegrafData.getTimestamp();

        fields.forEach((key, value) -> {
            Sample.SampleBuilder builder = Sample.builder();
            Sample sample = builder.name(name + "_" + key)
                    .timestamp(timestamp * 1000L)
                    .value(((Number) value).doubleValue())
                    .labels(immutableTags).build();

            sampleList.add(sample);
        });
        return sampleList;
    }

    @Test
    public void test() {

        List<Sample> convertedTelegraf1 = convertTelegraf(datum1);

        List<Sample> expectedTelegraf1 = new ArrayList<>();
        ImmutableMap<String, String> map = ImmutableMap.copyOf(Collections.singletonMap("host", "localHost"));
        Sample sample1 = new Sample("mem_total", map, 100, 1663391000);
        Sample sample2 = new Sample("mem_available_percent", map, 60, 1663391000);
        Sample sample3 = new Sample("mem_available", map, 60, 1663391000);
        Sample sample4 = new Sample("mem_used_percent", map, 40, 1663391000);
        Sample sample5 = new Sample("mem_used", map, 40, 1663391000);

        expectedTelegraf1.add(sample1);
        expectedTelegraf1.add(sample2);
        expectedTelegraf1.add(sample3);
        expectedTelegraf1.add(sample4);
        expectedTelegraf1.add(sample5);

        assertEquals(expectedTelegraf1, convertedTelegraf1);
    }

    @Builder(toBuilder = true)
    @EqualsAndHashCode
    @ToString
    @Getter
    @AllArgsConstructor
    static class Sample {
        final String name;
        final ImmutableMap<String, String> labels;
        final double value;
        final long timestamp;
    }

}