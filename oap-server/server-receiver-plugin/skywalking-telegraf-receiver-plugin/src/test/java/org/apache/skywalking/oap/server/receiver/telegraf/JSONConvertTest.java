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


//    @Ignore
//    public ImmutableMap<String, SampleFamily> convert(TelegrafData telegrafData) {
//
//        List<Sample> allSamples = new ArrayList<>();
//
//        List<TelegrafDatum> metrics = telegrafData.getMetrics();
//        for (TelegrafDatum m : metrics) {
//            List<Sample> samples = convertTelegraf(m);
//            allSamples.addAll(samples);
//
//            // Grouping all samples by their name, then build sampleFamily
//            Map<String, List<Sample>> sampleFamilyCollection = allSamples.stream()
//                    .collect(Collectors.groupingBy(Sample::getName));
//            ImmutableMap.Builder<String, SampleFamily> builder = ImmutableMap.builder();
//            sampleFamilyCollection.forEach((k, v) -> builder.put(k, SampleFamilyBuilder.newBuilder(v.toArray(new Sample[0])).build()));
//            return builder.build();
//        }
//    }







//
//package org.apache.skywalking.oap.server.receiver.telegraf;
//
//        import com.google.common.collect.ImmutableMap;
//        import lombok.*;
//        import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
//        import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
//        import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.TelegrafServiceHandler;
//        import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafData;
//        import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafDatum;
//        import org.junit.Before;
//        import org.junit.Ignore;
//        import org.junit.Test;
//
//        import java.util.*;
//        import java.util.stream.Collectors;
//
//        import static org.junit.Assert.assertEquals;
//
//public class JSONConvertTest {
//
//    private TelegrafDatum datum1;
////    private TelegrafDatum datum2;
////    private TelegrafData data;
//
//    @Before
//    public void initData() {
//        // The first telegrafDatum
//        datum1 = new TelegrafDatum();
//
//        Map<String, Object> fields1 = new HashMap<>();
//        fields1.put("available", 60);
//        fields1.put("available_percent", 60);
//        fields1.put("total", 100);
//        fields1.put("used", 40);
//        fields1.put("used_percent", 40);
//
//        Map<String, String> tags1 = new HashMap<>();
//        tags1.put("host", "localHost");
//
//        datum1.setFields(fields1);
//        datum1.setName("mem");
//        datum1.setTags(tags1);
//        datum1.setTimestamp(1663391);
//
////        // The second telegrafDatum
////        datum2 = new TelegrafDatum();
////
////        Map<String, Object> fields2 = new HashMap<>();
////        fields2.put("available", "4027759304");
////        fields2.put("available_percent", "26.41215070500567");
////        fields2.put("total", "14578163120");
////        fields2.put("used", "12330409216");
////        fields2.put("used_percent", "73.58654929499433");
////
////        Map<String, String> tags2 = new HashMap<>();
////        tags2.put("host", "local-host");
////
////        datum2.setFields(fields2);
////        datum2.setName("mem");
////        datum2.setTags(tags2);
////        datum2.setTimestamp(1666591390);
////
////        // The telegrafData
////        List<TelegrafDatum> datum = new ArrayList<>();
////        datum.add(datum1);
////        datum.add(datum2);
////
////        data = new TelegrafData();
////        data.setMetrics(datum);
//    }
//
//
//    /**
//     * The convert function in the {@link TelegrafServiceHandler}
//     **/
//    @Ignore
//    public List<Sample> convertTelegraf(TelegrafDatum telegrafData) {
//
//        List<Sample> sampleList = new ArrayList<>();
//
//        Map<String, Object> fields = telegrafData.getFields();
//        String name = telegrafData.getName();
//        Map<String, String> tags = telegrafData.getTags();
//        ImmutableMap<String, String> immutableTags = ImmutableMap.copyOf(tags);
//        long timestamp = telegrafData.getTimestamp();
//
//        fields.forEach((key, value) -> {
//            Sample.SampleBuilder builder = Sample.builder();
//            Sample sample = builder.name(name + "_" + key)
//                    .timestamp(timestamp * 1000L)
//                    .value(((Number) value).doubleValue())
//                    .labels(immutableTags).build();
//
//            sampleList.add(sample);
//        });
//        return sampleList;
//    }
//
//    @Test
//    public void test() {
//
//        List<Sample> convertedTelegraf1 = convertTelegraf(datum1);
//
//        List<Sample> expectedTelegraf1 = new ArrayList<>();
//        ImmutableMap<String, String> map = ImmutableMap.copyOf(Collections.singletonMap("host", "localHost"));
//        Sample sample1 = new Sample("mem_total", map, 100, 1663391000);
//        Sample sample2 = new Sample("mem_available_percent", map, 60, 1663391000);
//        Sample sample3 = new Sample("mem_available", map, 60, 1663391000);
//        Sample sample4 = new Sample("mem_used_percent", map, 40, 1663391000);
//        Sample sample5 = new Sample("mem_used", map, 40, 1663391000);
//
//        expectedTelegraf1.add(sample1);
//        expectedTelegraf1.add(sample2);
//        expectedTelegraf1.add(sample3);
//        expectedTelegraf1.add(sample4);
//        expectedTelegraf1.add(sample5);
//
//        assertEquals(expectedTelegraf1, convertedTelegraf1);
//    }
//
//}
//
//@Builder(toBuilder = true)
//@EqualsAndHashCode
//@ToString
//@Getter
//@AllArgsConstructor
//class Sample {
//    final String name;
//    final ImmutableMap<String, String> labels;
//    final double value;
//    final long timestamp;
//}
//
//
////    @Ignore
////    public ImmutableMap<String, SampleFamily> convert(TelegrafData telegrafData) {
////
////        List<Sample> allSamples = new ArrayList<>();
////
////        List<TelegrafDatum> metrics = telegrafData.getMetrics();
////        for (TelegrafDatum m : metrics) {
////            List<Sample> samples = convertTelegraf(m);
////            allSamples.addAll(samples);
////
////            // Grouping all samples by their name, then build sampleFamily
////            Map<String, List<Sample>> sampleFamilyCollection = allSamples.stream()
////                    .collect(Collectors.groupingBy(Sample::getName));
////            ImmutableMap.Builder<String, SampleFamily> builder = ImmutableMap.builder();
////            sampleFamilyCollection.forEach((k, v) -> builder.put(k, SampleFamilyBuilder.newBuilder(v.toArray(new Sample[0])).build()));
////            return builder.build();
////        }
////    }

