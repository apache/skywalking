package org.apache.skywalking.apm.agent.core.logging.core;

import com.google.gson.Gson;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

public class LoggingBenchmark {
    private static final PatternLogger patternLogger = new PatternLogger(LoggingBenchmark.class, PatternLogger.DEFAULT_PATTERN) {
        @Override
        protected void logger(LogLevel level, String message, Throwable e) {
            format(level, message, e);
        }
    };

    private static final JsonLogger jsonLogger = new JsonLogger(LoggingBenchmark.class, new Gson()) {
        @Override
        protected void logger(LogLevel level, String message, Throwable e) {
            generateJson(level, message, e);
        }
    };

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void jsonLogger() {
        jsonLogger.info("Hello World");
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void patternLogger() {
        patternLogger.info("Hello World");
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
