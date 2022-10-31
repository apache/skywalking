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
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class SampleConvertTest {

    /**
     * The convert function in the {@link TelegrafServiceHandler}
     **/
    @Ignore
    public List<SampleConvertTest.Sample> convertTelegraf(TelegrafDatum telegrafData) {

        List<SampleConvertTest.Sample> sampleList = new ArrayList<>();

        Map<String, Object> fields = telegrafData.getFields();
        String name = telegrafData.getName();
        Map<String, String> tags = telegrafData.getTags();
        ImmutableMap<String, String> immutableTags = ImmutableMap.copyOf(tags);
        long timestamp = telegrafData.getTimestamp();

        fields.forEach((key, value) -> {
            SampleConvertTest.Sample.SampleBuilder builder = SampleConvertTest.Sample.builder();
            SampleConvertTest.Sample sample = builder.name(name + "_" + key)
                    .timestamp(timestamp * 1000L)
                    .value(((Number) value).doubleValue())
                    .labels(immutableTags).build();

            sampleList.add(sample);
        });
        return sampleList;
    }

    /**
     * The convert sample function in the {@link TelegrafServiceHandler}
     **/
    @Ignore
    public Map<String, List<SampleConvertTest.Sample>> convertToSample(String httpMessage) {

        List<SampleConvertTest.Sample> allSamples = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node;
        try {
            node = mapper.readTree(httpMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Read each metrics json and convert it to Sample
        JsonNode metrics = node.get("metrics");
        for (JsonNode m : metrics) {
            TelegrafDatum telegrafDatum = mapper.convertValue(m, TelegrafDatum.class);
            List<SampleConvertTest.Sample> samples = convertTelegraf(telegrafDatum);
            allSamples.addAll(samples);
        }

        // Grouping all samples by their name
        return allSamples.stream()
                .collect(Collectors.groupingBy(Sample::getName));
    }

    @Test
    public void test() {

        String validHttpMessage = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"available\":5047739904,\"available_percent\":43.41215070500567,\"total\":46078149120,\"used\":45030409216,\"used_percent\":23.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        Map<String, List<SampleConvertTest.Sample>> convertedSample1 = convertToSample(validHttpMessage);

        ImmutableMap<String, String> map = ImmutableMap.copyOf(Collections.singletonMap("host", "localHost"));
        Sample sample1 = new Sample("mem_used", map, 1.1030409216E10, 1663391390000L);
        Sample sample2 = new Sample("mem_used", map, 4.5030409216E10, 1453365320000L);
        Sample sample3 = new Sample("mem_available_percent", map, 35.41215070500567, 1663391390000L);
        Sample sample4 = new Sample("mem_available_percent", map, 43.41215070500567, 1453365320000L);
        Sample sample5 = new Sample("mem_used_percent", map, 64.58784929499433, 1663391390000L);
        Sample sample6 = new Sample("mem_used_percent", map, 23.58784929499433, 1453365320000L);
        Sample sample7 = new Sample("mem_available", map, 6.047739904E9, 1663391390000L);
        Sample sample8 = new Sample("mem_available", map, 5.047739904E9, 1453365320000L);
        Sample sample9 = new Sample("mem_total", map, 1.707814912E10, 1663391390000L);
        Sample sample10 = new Sample("mem_total", map, 4.607814912E10, 1453365320000L);

        Map<String, List<SampleConvertTest.Sample>> expectedSampleCollection = new HashMap<>();
        List<SampleConvertTest.Sample> expectedSamples1 = new ArrayList<>();
        expectedSamples1.add(sample1);
        expectedSamples1.add(sample2);
        expectedSampleCollection.put("mem_used", expectedSamples1);
        List<SampleConvertTest.Sample> expectedSamples2 = new ArrayList<>();
        expectedSamples2.add(sample3);
        expectedSamples2.add(sample4);
        expectedSampleCollection.put("mem_available_percent", expectedSamples2);
        List<SampleConvertTest.Sample> expectedSamples3 = new ArrayList<>();
        expectedSamples3.add(sample5);
        expectedSamples3.add(sample6);
        expectedSampleCollection.put("mem_used_percent", expectedSamples3);
        List<SampleConvertTest.Sample> expectedSamples4 = new ArrayList<>();
        expectedSamples4.add(sample7);
        expectedSamples4.add(sample8);
        expectedSampleCollection.put("mem_available", expectedSamples4);
        List<SampleConvertTest.Sample> expectedSamples5 = new ArrayList<>();
        expectedSamples5.add(sample9);
        expectedSamples5.add(sample10);
        expectedSampleCollection.put("mem_total", expectedSamples5);

        assertEquals(expectedSampleCollection, convertedSample1);
    }

    @Test(expected = java.lang.Exception.class)
    public void test2() {
        //invalid http message
        String invalidHttpMessage = "http error";
        convertToSample(invalidHttpMessage);
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
