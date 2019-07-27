package org.apache.skywalking.apm.agent.core.context;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier.HeaderVersion;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
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
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import com.google.common.collect.Maps;



@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ContextCarrierBenchmark {


    @Benchmark
    public String newway() {
        final Map<String, String> headers = getHeaders();
        ContextCarrier contextCarrier =  null;
        CarrierItem next = ContextCarrier.topItem(null);
        do {
            final String headKey = next.getHeadKey();
            final String hv = headers.get(headKey);
            if (hv == null) {
                continue;
            }
            next.setHeadValue(hv, contextCarrier == null ? contextCarrier = new ContextCarrier() : contextCarrier);
            headers.remove(headKey);
        } while ((next = next.next()) != null);
        
        withSpan(contextCarrier);
        
        return "";
    }

    @Benchmark
    public String newway2() {
        final Map<String, String> headers = getHeaders();
        ContextCarrier contextCarrier =  null;
        final HeaderVersion[] headerNames = ContextCarrier.getHeaderVersions();
        for (final ContextCarrier.HeaderVersion h : headerNames) {
            final String headKey = h.getHeadKey();
            final String hv = headers.get(headKey);
            if (hv == null) {
                continue;
            }
            if (contextCarrier == null) {
                contextCarrier =  new ContextCarrier();
            }
            contextCarrier.deserialize(hv, h);
            headers.remove(headKey);
        } 
        withSpan(contextCarrier);
        return "";
    }

    private static final boolean WITH_HEADER_ON = Boolean.getBoolean("with_header_on");
    
    protected Map<String, String> getHeaders() {
        final Map<String,String> headers = Maps.newHashMap();
        if (WITH_HEADER_ON) {
            headers.put(SW6CarrierItem.HEADER_NAME, "1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw--");
        }   
        return headers;
    }

    private static final boolean WITH_SPAN_ON = Boolean.getBoolean("with_span_on");

    private static void withSpan(final ContextCarrier contextCarrier) {
        if (WITH_SPAN_ON) {
            final AbstractSpan span = ContextManager.createEntrySpan("test", contextCarrier);
            ContextManager.stopSpan();
        }
    }

    @Benchmark
    public String oldway() {
        final Map<String, String> headers = getHeaders();
        ContextCarrier contextCarrier =  new ContextCarrier();
        CarrierItem next =  contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(headers.get(next.getHeadKey()));
            headers.remove(next.getHeadKey());
        }
        withSpan(contextCarrier);
        return "";
    }

    @Benchmark
    public String oldwayImprove() {
        final Map<String, String> headers = getHeaders();
        ContextCarrier contextCarrier =  new ContextCarrier();
        CarrierItem next =  contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            final String headKey = next.getHeadKey();
            final String hv = headers.get(headKey);
            if (hv == null) {
                continue;
            }
            next.setHeadValue(hv);
            headers.remove(headKey);
        }
        withSpan(contextCarrier);
        return "";
    }

    @Benchmark
    public String oldwayImprove2() {
        final Map<String, String> headers = getHeaders();
        final ContextCarrier contextCarrier =  new ContextCarrier();
        CarrierItem next =  contextCarrier.topItem();
        do {
            final String headKey = next.getHeadKey();
            final String hv = headers.get(headKey);
            if (hv == null) {
                continue;
            }
            next.setHeadValue(hv);
            headers.remove(headKey);
        } while ((next = next.next()) != null);
        withSpan(contextCarrier);
        return "";
    }

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder()
                        .include(ContextCarrierBenchmark.class.getSimpleName())
                        .mode(Mode.Throughput)
                        .warmupIterations(8)
                        .measurementIterations(8)
                        .forks(1)
                        .measurementTime(TimeValue.seconds(1))
            .warmupTime(TimeValue.seconds(1))
            .threads(4)//.addProfiler("gc")
                        .timeUnit(TimeUnit.MILLISECONDS)
                        .build();
        new Runner(opt).run();
    }

/*
 mvn package exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.args="org.apache.skywalking.apm.agent.core.context.ContextCarrierBenchmark  -w 1 -r 1   -wi 5 -i 10 -f 0 -tu ms"  -Dexec.classpathScope=test  -f apm-sniffer/apm-agent-core/pom.xml -DskipTests
 
 with_span_on=false
 
# JMH version: 1.21
# VM version: JDK 1.8.0_211, Java HotSpot(TM) 64-Bit Server VM, 25.211-b12
# VM invoker: D:\JDK\jdk1.8.0X64\jre\bin\java.exe
# Warmup: 8 iterations, 1 s each
# Measurement: 8 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                Mode  Cnt       Score      Error   Units  Score/min
ContextCarrierBenchmark.newway          thrpt   10  477082.852 ± 2127.642  ops/ms      6.361
ContextCarrierBenchmark.newway2         thrpt   10  475141.703 ± 1845.183  ops/ms      6.335
ContextCarrierBenchmark.oldway          thrpt   10   75003.261 ± 3396.205  ops/ms      1.000
ContextCarrierBenchmark.oldwayImprove   thrpt   10   76286.424 ± 1561.876  ops/ms      1.017
ContextCarrierBenchmark.oldwayImprove2  thrpt   10  224132.018 ± 1449.960  ops/ms      2.988
=========
# JMH version: 1.21
# VM version: JDK 11.0.3, Java HotSpot(TM) 64-Bit Server VM, 11.0.3+12-LTS
# VM invoker: D:\JDK\jdk-11.x\bin\java.exe
# Warmup: 5 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time


Benchmark                                Mode  Cnt       Score      Error   Units  Score/min
ContextCarrierBenchmark.newway          thrpt   10  417856.131 ± 2268.863  ops/ms      9.744
ContextCarrierBenchmark.newway2         thrpt   10  472036.761 ± 2103.097  ops/ms     11.007
ContextCarrierBenchmark.oldway          thrpt   10   42884.880 ± 1580.783  ops/ms      1.000
ContextCarrierBenchmark.oldwayImprove   thrpt   10   60721.049 ±  132.725  ops/ms      1.416
ContextCarrierBenchmark.oldwayImprove2  thrpt   10  219660.354 ± 1373.858  ops/ms      5.122

===================
===================

java8 -Dwith_span_on=true

Benchmark                                Mode  Cnt      Score     Error   Units  Score/min
ContextCarrierBenchmark.newway          thrpt   10  20831.184 ± 445.370  ops/ms      1.272
ContextCarrierBenchmark.newway2         thrpt   10  21490.402 ± 471.728  ops/ms      1.312
ContextCarrierBenchmark.oldway          thrpt   10  16379.571 ± 372.807  ops/ms      1.000
ContextCarrierBenchmark.oldwayImprove   thrpt   10  16489.840 ± 370.032  ops/ms      1.007
ContextCarrierBenchmark.oldwayImprove2  thrpt   10  19885.463 ± 274.906  ops/ms      1.214
 */

}
