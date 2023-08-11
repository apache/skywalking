# Java Microbenchmark Harness (JMH)
JMH is a Java harness for building, running, and analysing nano/micro/milli/macro benchmarks written in Java and other languages targeting the JVM.

We have a module called `microbench` which performs a series of micro-benchmark tests for JMH testing.
Make new JMH tests extend the `org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark`
to customize runtime conditions (Measurement, Fork, Warmup, etc.).

You can build the jar with command `./mvnw -Dmaven.test.skip -DskipTests -pl :microbench package -am -Pbenchmark`.

JMH tests could run as a normal unit test. And they could run as an independent uber jar via `java -jar benchmark.jar` for all benchmarks,
or via `java -jar /benchmarks.jar exampleClassName` for a specific test.

Output test results in JSON format, you can add `-rf json` like `java -jar benchmarks.jar -rf json`, if you run through the IDE, you can configure the `-DperfReportDir=savePath` parameter to set the JMH report result save path, a report results in JSON format will be generated when the run ends.

More information about JMH can be found here: [jmh docs](https://openjdk.java.net/projects/code-tools/jmh/).
