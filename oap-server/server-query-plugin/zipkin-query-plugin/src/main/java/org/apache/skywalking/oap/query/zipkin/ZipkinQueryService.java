package org.apache.skywalking.oap.query.zipkin;

import com.google.protobuf.InvalidProtocolBufferException;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryV2DAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.storage.QueryRequest;

public class ZipkinQueryService {
    private final ModuleManager moduleManager;
    private IZipkinQueryDAO zipkinQueryDAO;
    private ISpanAttachedEventQueryDAO spanAttachedEventQueryDAO;
    private boolean supportTraceV2 = false;

    public ZipkinQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IZipkinQueryDAO getZipkinQueryDAO() {
        if (zipkinQueryDAO == null) {
            zipkinQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IZipkinQueryDAO.class);
            if (IZipkinQueryV2DAO.class.isAssignableFrom(zipkinQueryDAO.getClass())) {
                this.supportTraceV2 = true;
            }
        }
        return zipkinQueryDAO;
    }

    private ISpanAttachedEventQueryDAO getSpanAttachedEventQueryDAO() {
        if (spanAttachedEventQueryDAO == null) {
            this.spanAttachedEventQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ISpanAttachedEventQueryDAO.class);
        }
        return spanAttachedEventQueryDAO;
    }

    public List<String> getServiceNames() throws IOException {
        return getZipkinQueryDAO().getServiceNames();
    }

    public List<String> getRemoteServiceNames(String serviceName) throws IOException {
        return getZipkinQueryDAO().getRemoteServiceNames(serviceName);
    }

    public List<String> getSpanNames(String serviceName) throws IOException {
        return getZipkinQueryDAO().getSpanNames(serviceName);
    }

    public List<Span> getTraceById(String traceId) throws IOException {
        IZipkinQueryDAO zipkinQueryDAO = getZipkinQueryDAO();
        List<Span> trace;
        if (supportTraceV2) {
            List<SpanWrapper> wrappedTrace = ((IZipkinQueryV2DAO) zipkinQueryDAO).getTraceV2(
                Span.normalizeTraceId(traceId.trim()), null);
            TraceV2 traceV2 = buildTraceV2(wrappedTrace);
            trace = traceV2.getSpans();
            appendEventsDebuggable(trace, traceV2.getEvents());
        } else {
            trace = getZipkinQueryDAO().getTraceDebuggable(Span.normalizeTraceId(traceId.trim()), null);
            if (CollectionUtils.isEmpty(trace)) {
                return trace;
            }
            List<SpanAttachedEventRecord> eventRecords = getSpanAttachedEventQueryDAO().queryZKSpanAttachedEventsDebuggable(
                SpanAttachedEventTraceType.ZIPKIN, Arrays.asList(Span.normalizeTraceId(traceId.trim())), null);
            List<SpanAttachedEvent> events = new ArrayList<>(eventRecords.size());
            for (SpanAttachedEventRecord eventRecord : eventRecords) {
                events.add(SpanAttachedEvent.parseFrom(eventRecord.getDataBinary()));
            }
            appendEventsDebuggable(trace, events);
        }
        return trace;
    }

    public List<List<Span>> getTraces(QueryRequest queryRequest, Duration duration) throws IOException {
        IZipkinQueryDAO zipkinQueryDAO = getZipkinQueryDAO();
        List<List<Span>> traces;
        if (supportTraceV2) {
            traces = new ArrayList<>();
            List<List<SpanWrapper>> wrappedTraces = ((IZipkinQueryV2DAO) zipkinQueryDAO).getTracesV2(queryRequest, duration);
            for (List<SpanWrapper> wrappedTrace : wrappedTraces) {
                TraceV2 traceV2 =  buildTraceV2(wrappedTrace);
                traces.add(traceV2.getSpans());
                appendEventsDebuggable(traceV2.getSpans(), traceV2.events);
            }
        } else {
            traces = zipkinQueryDAO.getTracesDebuggable(queryRequest, duration);
            appendEventsToTracesDebuggable(traces);
        }
        return traces;
    }

    public List<List<Span>> getTracesByIds(Set<String> normalizeTraceIds) throws IOException {
        IZipkinQueryDAO zipkinQueryDAO = getZipkinQueryDAO();
        List<List<Span>> traces;
        if (supportTraceV2) {
            traces = new ArrayList<>();
            List<List<SpanWrapper>> wrappedTraces = ((IZipkinQueryV2DAO) zipkinQueryDAO).getTracesV2(normalizeTraceIds, null);
            for (List<SpanWrapper> wrappedTrace : wrappedTraces) {
                TraceV2 traceV2 =  buildTraceV2(wrappedTrace);
                traces.add(traceV2.getSpans());
                appendEventsDebuggable(traceV2.getSpans(), traceV2.events);
            }
        } else {
            traces = getZipkinQueryDAO().getTraces(normalizeTraceIds, null);
            appendEventsToTraces(traces);
        }
        return traces;
    }

    private void appendEventsToTracesDebuggable(List<List<Span>> traces) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            if (traceContext != null) {
                debuggingSpan = traceContext.createSpan("Query: appendEventsToTraces");
            }
            appendEventsToTraces(traces);
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);

            }
        }
    }

    private void appendEventsToTraces(List<List<Span>> traces) throws IOException {
        final Map<String, List<Span>> traceIdWithSpans = traces.stream().filter(CollectionUtils::isNotEmpty)
                                                               .collect(Collectors.toMap(s -> s.get(0).traceId(), Function.identity(), (s1, s2) -> s1));
        if (CollectionUtils.isEmpty(traceIdWithSpans)) {
            return;
        }

        final List<SpanAttachedEventRecord> records = getSpanAttachedEventQueryDAO().queryZKSpanAttachedEventsDebuggable(SpanAttachedEventTraceType.ZIPKIN,
                                                                                                                         new ArrayList<>(traceIdWithSpans.keySet()), null);
        List<SpanAttachedEvent> events = new ArrayList<>(records.size());
        for (SpanAttachedEventRecord record : records) {
            events.add(SpanAttachedEvent.parseFrom(record.getDataBinary()));
        }
        final Map<String, List<SpanAttachedEvent>> traceEvents = events.stream().collect(Collectors.groupingBy(e -> e.getTraceContext().getTraceId()));
        for (Map.Entry<String, List<SpanAttachedEvent>> entry : traceEvents.entrySet()) {
            appendEventsDebuggable(traceIdWithSpans.get(entry.getKey()), entry.getValue());
        }
    }

    private void appendEventsDebuggable(List<Span> spans, List<SpanAttachedEvent> events) throws InvalidProtocolBufferException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            if (traceContext != null) {
                debuggingSpan = traceContext.createSpan("Query: appendEvents");
            }
            appendEvents(spans, events);
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);
            }
        }
    }

    private void appendEvents(List<Span> spans, List<SpanAttachedEvent> events) throws InvalidProtocolBufferException {
        if (CollectionUtils.isEmpty(spans) || CollectionUtils.isEmpty(events)) {
            return;
        }

        final List<Tuple2<Integer, Span>> spanWithIndex = IntStream.range(0, spans.size()).mapToObj(i -> Tuple.of(i, spans.get(i))).collect(
            Collectors.toList());

        // sort by start time
        events.sort((e1, e2) -> {
            final int second = Long.compare(e1.getStartTime().getSeconds(), e2.getStartTime().getSeconds());
            if (second == 0) {
                return Long.compare(e1.getStartTime().getNanos(), e2.getStartTime().getNanos());
            }
            return second;
        });

        final Map<String, List<SpanAttachedEvent>> namedEvents = events.stream()
                                                                       .collect(Collectors.groupingBy(SpanAttachedEvent::getEvent, Collectors.toList()));

        final Map<String, Tuple2<Span.Builder, Integer>> spanCache = new HashMap<>();
        for (Map.Entry<String, List<SpanAttachedEvent>> namedEntry : namedEvents.entrySet()) {
            for (int i = 1; i <= namedEntry.getValue().size(); i++) {
                final SpanAttachedEvent event = namedEntry.getValue().get(i - 1);
                String spanId = event.getTraceContext().getSpanId();
                String eventName = event.getEvent() + (namedEntry.getValue().size() == 1 ? "" : "-" + i);
                // final SpanAttachedEvent event = SpanAttachedEvent.parseFrom(record.getDataBinary());

                // find matched span
                Tuple2<Span.Builder, Integer> spanBuilder = spanCache.get(spanId);
                if (spanBuilder == null) {
                    Tuple2<Integer, Span> matchesSpan = spanWithIndex.stream().filter(s -> Objects.equals(s._2.id(), spanId)).
                                                                     findFirst().orElse(null);
                    if (matchesSpan == null) {
                        continue;
                    }

                    // if the event is server side, then needs to change to the upstream span
                    final String direction = getSpanAttachedEventTagValue(event.getTagsList(), "data_direction");
                    final String type = getSpanAttachedEventTagValue(event.getTagsList(), "data_type");
                    if (("request".equals(type) && "inbound".equals(direction)) || ("response".equals(type) && "outbound".equals(direction))) {
                        final String parentSpanId = matchesSpan._2.id();
                        matchesSpan = spanWithIndex.stream().filter(s -> Objects.equals(s._2.parentId(), parentSpanId)
                            && Objects.equals(s._2.kind(), Span.Kind.SERVER)).findFirst().orElse(matchesSpan);
                    }

                    spanBuilder = Tuple.of(matchesSpan._2.toBuilder(), matchesSpan._1);
                    spanCache.put(spanId, spanBuilder);
                }

                appendEventDebuggable(spanBuilder._1, eventName, event);
            }
        }

        // re-build modified spans
        for (Map.Entry<String, Tuple2<Span.Builder, Integer>> entry : spanCache.entrySet()) {
            spans.set(entry.getValue()._2, entry.getValue()._1.build());
        }
    }

    private void appendEventDebuggable(Span.Builder span, String eventName, SpanAttachedEvent event) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            if (traceContext != null) {
                debuggingSpan = traceContext.createSpan("Query : appendEvent");
            }
            appendEvent(span, eventName, event);
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);
            }
        }
    }

    private void appendEvent(Span.Builder span, String eventName, SpanAttachedEvent event) {
        span.addAnnotation(
            TimeUnit.SECONDS.toMicros(event.getStartTime().getSeconds()) + TimeUnit.NANOSECONDS.toMicros(event.getStartTime().getNanos()),
            "Start " + eventName);
        span.addAnnotation(
            TimeUnit.SECONDS.toMicros(event.getEndTime().getSeconds()) + TimeUnit.NANOSECONDS.toMicros(event.getEndTime().getNanos()),
            "Finished " + eventName);

        final Yaml yaml = new Yaml();
        if (event.getSummaryList().size() > 0) {
            final Map<String, Long> summaries = event.getSummaryList().stream().collect(Collectors.toMap(
                KeyIntValuePair::getKey, KeyIntValuePair::getValue, (s1, s2) -> s1));
            String summary = yaml.dumpAs(summaries, Tag.MAP, DumperOptions.FlowStyle.AUTO);
            span.putTag(formatEventTagKey(eventName + ".summary"), summary);
        }
        if (event.getTagsList().size() > 0) {
            final Map<String, String> tags = event.getTagsList().stream().collect(Collectors.toMap(
                KeyStringValuePair::getKey, KeyStringValuePair::getValue, (s1, s2) -> s1));
            String summary = yaml.dumpAs(tags, Tag.MAP, DumperOptions.FlowStyle.AUTO);
            span.putTag(formatEventTagKey(eventName + ".tags"), summary);
        }
    }

    private String formatEventTagKey(String name) {
        return name.replaceAll(" ", ".").toLowerCase(Locale.ROOT);
    }

    private String getSpanAttachedEventTagValue(List<KeyStringValuePair> values, String tagKey) {
        for (KeyStringValuePair pair : values) {
            if (Objects.equals(pair.getKey(), tagKey)) {
                return pair.getValue();
            }
        }
        return null;
    }

    private TraceV2 buildTraceV2(List<SpanWrapper> wrappedTrace) {
        TraceV2 traceV2 = new TraceV2();
        if (CollectionUtils.isEmpty(wrappedTrace)) {
            return traceV2;
        }
        List<SpanAttachedEvent> events = new ArrayList<>();
        for (SpanWrapper wrappedSpan : wrappedTrace) {
            if (wrappedSpan.getSource().equals(Source.ZIPKIN)) {
                traceV2.getSpans().add(SpanBytesDecoder.PROTO3.decodeOne(wrappedSpan.getSpan().toByteArray()));
            } else if (wrappedSpan.getSource().equals(Source.ZIPKIN_EVENT)) {
                try {
                    traceV2.getEvents().add(SpanAttachedEvent.parseFrom(wrappedSpan.getSpan()));
                } catch (InvalidProtocolBufferException e) {
                    // ignore
                }
            }
        }
        return traceV2;
    }

    @Getter
    static class TraceV2 {
        private List<Span> spans = new ArrayList<>();
        private List<SpanAttachedEvent> events = new ArrayList<>();
    }
}
