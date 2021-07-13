# Plugin automatic test framework

The plugin test framework is designed to verify the function and compatibility of plugins. As there are dozens of plugins and
hundreds of versions that need to be verified, it is impossible to do it manually.
The test framework uses container-based tech stack and requires a set of real services with the agents installed. Then, the test mock
OAP backend runs to check the segments data sent from agents.

Every plugin maintained in the main repo requires corresponding test cases as well as matching versions in the supported list doc.

## Environment Requirements

1. MacOS/Linux
2. JDK 8+
3. Docker
4. Docker Compose

## Case Base Image Introduction

The test framework provides `JVM-container` and `Tomcat-container` base images including JDK8 and JDK14. You can choose the best one for your test case. If both are suitable for your case, **`JVM-container` is preferred**.

### JVM-container Image Introduction

[JVM-container](../../../test/plugin/containers/jvm-container) uses `openjdk:8` as the base image. `JVM-container` supports JDK14, which inherits `openjdk:14`.
The test case project must be packaged as `project-name.zip`, including `startup.sh` and uber jar, by using `mvn clean package`.

Take the following test projects as examples:
* [sofarpc-scenario](../../../test/plugin/scenarios/sofarpc-scenario) is a single project case.
* [webflux-scenario](../../../test/plugin/scenarios/webflux-scenario) is a case including multiple projects.
* [jdk14-with-gson-scenario](../../../test/plugin/scenarios/jdk14-with-gson-scenario) is a single project case with JDK14.

### Tomcat-container Image Introduction

[Tomcat-container](../../../test/plugin/containers/tomcat-container) uses `tomcat:8.5.57-jdk8-openjdk` or `tomcat:8.5.57-jdk14-openjdk` as the base image.
The test case project must be packaged as `project-name.war` by using `mvn package`.

Take the following test project as an example
* [spring-4.3.x-scenario](https://github.com/apache/skywalking/tree/master/test/plugin/scenarios/spring-4.3.x-scenario)


## Test project hierarchical structure
The test case is an independent maven project, and it must be packaged as a war tar ball or zip file, depending on the chosen base image. Also, two external accessible endpoints usually two URLs) are required.

All test case codes should be in the `org.apache.skywalking.apm.testcase.*` package. If there are some codes expected to be instrumented, then the classes could be in the `test.org.apache.skywalking.apm.testcase.*` package.

**JVM-container test project hierarchical structure**

```
[plugin-scenario]
    |- [bin]
        |- startup.sh
    |- [config]
        |- expectedData.yaml
    |- [src]
        |- [main]
            |- ...
        |- [resource]
            |- log4j2.xml
    |- pom.xml
    |- configuration.yaml
    |- support-version.list

[] = directory
```

**Tomcat-container test project hierarchical structure**

```
[plugin-scenario]
    |- [config]
        |- expectedData.yaml
    |- [src]
        |- [main]
            |- ...
        |- [resource]
            |- log4j2.xml
        |- [webapp]
            |- [WEB-INF]
                |- web.xml
    |- pom.xml
    |- configuration.yaml
    |- support-version.list

[] = directory
```

## Test case configuration files
The following files are required in every test case.

File Name | Descriptions
---|---
`configuration.yml` | Declare the basic case information, including case name, entrance endpoints, mode, and dependencies.
`expectedData.yaml` | Describe the expected segmentItems.
`support-version.list` | List the target versions for this case.
`startup.sh` |`JVM-container` only. This is not required when using `Tomcat-container`.

`*` support-version.list format requires every line for a single version (contains only the last version number of each minor version). You may use `#` to comment out this version.

### configuration.yml

| Field | description
| --- | ---
| type | Image type, options, `jvm`, or `tomcat`. Required.
| entryService | The entrance endpoint (URL) for test case access. Required. (HTTP Method: GET)
| healthCheck | The health check endpoint (URL) for test case access. Required. (HTTP Method: HEAD)
| startScript | Path of the start up script. Required in `type: jvm` only.
| runningMode | Running mode with the optional plugin, options, `default`(default), `with_optional`, or `with_bootstrap`.
| withPlugins | Plugin selector rule, e.g.:`apm-spring-annotation-plugin-*.jar`. Required for `runningMode=with_optional` or `runningMode=with_bootstrap`.
| environment | Same as `docker-compose#environment`.
| depends_on | Same as `docker-compose#depends_on`.
| dependencies | Same as `docker-compose#services`, `image`, `links`, `hostname`, `environment` and `depends_on` are supported.

**Note:, `docker-compose` activates only when `dependencies` is blank.**

**runningMode** option description.

| Option | description
| --- | ---
| default | Activate all plugins in `plugin` folder like the official distribution agent. 
| with_optional | Activate `default` and plugins in `optional-plugin` by the give selector.
| with_bootstrap | Activate `default` and plugins in `bootstrap-plugin` by the give selector.

with_optional/with_bootstrap supports multiple selectors, separated by `;`.

**File Format**

```
type:
entryService:
healthCheck:
startScript:
runningMode:
withPlugins:
environment:
  ...
depends_on:
  ...
dependencies:
  service1:
    image:
    hostname: 
    expose:
      ...
    environment:
      ...
    depends_on:
      ...
    links:
      ...
    entrypoint:
      ...
    healthcheck:
      ...
```

* dependencies support docker compose `healthcheck`. But the format is a little different. We need to have `-` as the start of every config item,
and describe it as a string line.

For example, in the official document, the health check is:
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost"]
  interval: 1m30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

Here you should write:
```yaml
healthcheck:
  - 'test: ["CMD", "curl", "-f", "http://localhost"]'
  - "interval: 1m30s"
  - "timeout: 10s"
  - "retries: 3"
  - "start_period: 40s"
```

In some cases, the dependency service (usually a third-party server like the SolrJ server) is required to keep the same version
as the client lib version, which is defined as `${test.framework.version}` in pom. You may use `${CASE_SERVER_IMAGE_VERSION}`
as the version number, which will be changed in the test for each version.

> It does not support resource related configurations, such as volumes, ports, and ulimits. The reason for this is that in test scenarios, no mapping is required for any port to the host VM, or to mount any folder.

**Take the following test cases as examples:**
* [dubbo-2.7.x with JVM-container](../../../test/plugin/scenarios/dubbo-2.7.x-scenario/configuration.yml)
* [jetty with JVM-container](../../../test/plugin/scenarios/jetty-scenario/configuration.yml)
* [gateway with runningMode](../../../test/plugin/scenarios/gateway-2.1.x-scenario/configuration.yml)
* [canal with docker-compose](../../../test/plugin/scenarios/canal-scenario/configuration.yml)

### expectedData.yaml

**Operator for number**

| Operator | Description |
| :--- | :--- |
| `nq` | Not equal |
| `eq` | Equal(default) |
| `ge` | Greater than or equal |
| `gt` | Greater than |

**Operator for String**

| Operator | Description |
| :--- | :--- |
| `not null` | Not null |
| `null` | Null or empty String |
| `eq` | Equal(default) |

**Expected Data Format Of The Segment**
```yml
segmentItems:
-
  serviceName: SERVICE_NAME(string)
  segmentSize: SEGMENT_SIZE(int)
  segments:
  - segmentId: SEGMENT_ID(string)
    spans:
        ...
```


| Field |  Description
| --- | ---  
| serviceName | Service Name.
| segmentSize | The number of segments is expected.
| segmentId | Trace ID.
| spans | Segment span list. In the next section, you will learn how to describe each span.

**Expected Data Format Of The Span**

**Note**: The order of span list should follow the order of the span finish time.

```yml
    operationName: OPERATION_NAME(string)
    parentSpanId: PARENT_SPAN_ID(int)
    spanId: SPAN_ID(int)
    startTime: START_TIME(int)
    endTime: END_TIME(int)
    isError: IS_ERROR(string: true, false)
    spanLayer: SPAN_LAYER(string: DB, RPC_FRAMEWORK, HTTP, MQ, CACHE)
    spanType: SPAN_TYPE(string: Exit, Entry, Local)
    componentId: COMPONENT_ID(int)
    tags:
    - {key: TAG_KEY(string), value: TAG_VALUE(string)}
    ...
    logs:
    - {key: LOG_KEY(string), value: LOG_VALUE(string)}
    ...
    peer: PEER(string)
    refs:
    - {
       traceId: TRACE_ID(string),
       parentTraceSegmentId: PARENT_TRACE_SEGMENT_ID(string),
       parentSpanId: PARENT_SPAN_ID(int),
       parentService: PARENT_SERVICE(string),
       parentServiceInstance: PARENT_SERVICE_INSTANCE(string),
       parentEndpoint: PARENT_ENDPOINT_NAME(string),
       networkAddress: NETWORK_ADDRESS(string),
       refType:  REF_TYPE(string: CrossProcess, CrossThread)
     }
   ...
```

| Field | Description 
|--- |--- 
| operationName | Span Operation Name.
| parentSpanId | Parent span ID. **Note**: The parent span ID of the first span should be -1. 
| spanId | Span ID. **Note**: Start from 0. 
| startTime | Span start time. It is impossible to get the accurate time, not 0 should be enough.
| endTime | Span finish time. It is impossible to get the accurate time, not 0 should be enough.
| isError | Span status, true or false. 
| componentId | Component id for your plugin. 
| tags | Span tag list. **Notice**, Keep in the same order as the plugin coded.
| logs | Span log list. **Notice**, Keep in the same order as the plugin coded.
| SpanLayer | Options, DB, RPC_FRAMEWORK, HTTP, MQ, CACHE.
| SpanType | Span type, options, Exit, Entry or Local.
| peer | Remote network address, IP + port mostly. For exit span, this should be required. 

The verify description for SegmentRef

| Field | Description 
|---- |---- 
| traceId | 
| parentTraceSegmentId | Parent SegmentId, pointing to the segment id in the parent segment.
| parentSpanId | Parent SpanID, pointing to the span id in the parent segment.
| parentService | The service of parent/downstream service name.
| parentServiceInstance | The instance of parent/downstream service instance name.
| parentEndpoint |  The endpoint of parent/downstream service.
| networkAddress | The peer value of parent exit span.
| refType | Ref type, options, CrossProcess or CrossThread.

**Expected Data Format Of The Meter Items**
```yml
meterItems:
-
  serviceName: SERVICE_NAME(string)
  meterSize: METER_SIZE(int)
  meters:
  - ...
```

| Field |  Description
| --- | ---  
| serviceName | Service Name.
| meterSize | The number of meters is expected.
| meters | meter list. Follow the next section to see how to describe every meter.

**Expected Data Format Of The Meter**

```yml
    meterId: 
        name: NAME(string)
        tags:
        - {name: TAG_NAME(string), value: TAG_VALUE(string)}
    singleValue: SINGLE_VALUE(double)
    histogramBuckets:
    - HISTOGRAM_BUCKET(double)
    ...
```

The verify description for MeterId

| Field | Description 
|--- |--- 
| name | meter name.
| tags | meter tags.
| tags.name | tag name.
| tags.value | tag value.
| singleValue | counter or gauge value. Using condition operate of the number to validate, such as `gt`, `ge`. If current meter is histogram, don't need to write this field.
| histogramBuckets | histogram bucket. The bucket list must be ordered. The tool assert at least one bucket of the histogram having nonzero count. If current meter is counter or gauge, don't need to write this field.

### startup.sh

This script provide a start point to JVM based service, most of them starts by a `java -jar`, with some variables.
The following system environment variables are available in the shell.

| Variable   | Description    |
|:----     |:----        |
| agent_opts               |     Agent plugin opts, check the detail in plugin doc or the same opt added in this PR.        |
| SCENARIO_NAME       |  Service name. Default same as the case folder name    |
| SCENARIO_VERSION           | Version |
| SCENARIO_ENTRY_SERVICE             | Entrance URL to access this service |
| SCENARIO_HEALTH_CHECK_URL          | Health check URL  |


> `${agent_opts}` is required to add into your `java -jar` command, which including the parameter injected by test framework, and
> make agent installed. All other parameters should be added after `${agent_opts}`.

The test framework will set the service name as the test case folder name by default, but in some cases, there are more 
than one test projects are required to run in different service codes, could set it explicitly like the following example.

Example
```bash
home="$(cd "$(dirname $0)"; pwd)"

java -jar ${agent_opts} "-Dskywalking.agent.service_name=jettyserver-scenario" ${home}/../libs/jettyserver-scenario.jar &
sleep 1

java -jar ${agent_opts} "-Dskywalking.agent.service_name=jettyclient-scenario"  ${home}/../libs/jettyclient-scenario.jar &

```

> Only set this or use other skywalking options when it is really necessary.

**Take the following test cases as examples**
* [undertow](../../../test/plugin/scenarios/undertow-scenario/bin/startup.sh)
* [webflux](../../../test/plugin/scenarios/webflux-scenario/webflux-dist/bin/startup.sh)


## Best Practices

### How To Use The Archetype To Create A Test Case Project
We provided archetypes and a script to make creating a project easier. It creates a completed project of a test case. So that we only need to focus on cases.
First, we can use followed command to get usage about the script.

`bash ${SKYWALKING_HOME}/test/plugin/generator.sh`

Then, runs and generates a project, named by `scenario_name`, in `./scenarios`.


### Recommendations for pom

```xml
    <properties>
        <!-- Provide and use this property in the pom. -->
        <!-- This version should match the library version, -->
        <!-- in this case, http components lib version 4.3. -->
        <test.framework.version>4.3</test.framework.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.httpcomponents</groupId>
            <artifactId>httpclient</artifactId>
            <version>${test.framework.version}</version>
        </dependency>
        ...
    </dependencies>

    <build>
        <!-- Set the package final name as same as the test case folder case. -->
        <finalName>httpclient-4.3.x-scenario</finalName>
        ....
    </build>
```

### How To Implement Heartbeat Service

Heartbeat service is designed for checking the service available status. This service is a simple HTTP service, returning 200 means the
target service is ready. Then the traffic generator will access the entry service and verify the expected data.
User should consider to use this service to detect such as whether the dependent services are ready, especially when 
dependent services are database or cluster.

Notice, because heartbeat service could be traced fully or partially, so, segmentSize in `expectedData.yaml` should use `ge` as the operator,
and don't include the segments of heartbeat service in the expected segment data.

### The example Process of Writing Tracing Expected Data

Expected data file, `expectedData.yaml`, include `SegmentItems` part.

We are using the HttpClient plugin to show how to write the expected data.

There are two key points of testing
1. Whether is HttpClient span created.
1. Whether the ContextCarrier created correctly, and propagates across processes.

```
+-------------+         +------------------+            +-------------------------+
|   Browser   |         |  Case Servlet    |            | ContextPropagateServlet |
|             |         |                  |            |                         |
+-----|-------+         +---------|--------+            +------------|------------+
      |                           |                                  |
      |                           |                                  |
      |       WebHttp            +-+                                 |
      +------------------------> |-|         HttpClient             +-+
      |                          |--------------------------------> |-|
      |                          |-|                                |-|
      |                          |-|                                |-|
      |                          |-| <--------------------------------|
      |                          |-|                                +-+
      | <--------------------------|                                 |
      |                          +-+                                 |
      |                           |                                  |
      |                           |                                  |
      |                           |                                  |
      |                           |                                  |
      +                           +                                  +
```
#### segmentItems

By following the flow of HttpClient case, there should be two segments created.
1. Segment represents the CaseServlet access. Let's name it as `SegmentA`.
1. Segment represents the ContextPropagateServlet access. Let's name it as `SegmentB`.

```yml
segmentItems:
  - serviceName: httpclient-case
    segmentSize: ge 2 # Could have more than one health check segments, because, the dependency is not standby.
```

Because Tomcat plugin is a default plugin of SkyWalking, so, in SegmentA, there are two spans
1. Tomcat entry span
1. HttpClient exit span

SegmentA span list should like following
```yml
    - segmentId: not null
      spans:
        - operationName: /httpclient-case/case/context-propagate
          parentSpanId: 0
          spanId: 1
          startTime: nq 0
          endTime: nq 0
          isError: false
          spanLayer: Http
          spanType: Exit
          componentId: eq 2
          tags:
            - {key: url, value: 'http://127.0.0.1:8080/httpclient-case/case/context-propagate'}
            - {key: http.method, value: GET}
          logs: []
          peer: 127.0.0.1:8080
        - operationName: /httpclient-case/case/httpclient
          parentSpanId: -1
          spanId: 0
          startTime: nq 0
          endTime: nq 0
          spanLayer: Http
          isError: false
          spanType: Entry
          componentId: 1
          tags:
            - {key: url, value: 'http://localhost:{SERVER_OUTPUT_PORT}/httpclient-case/case/httpclient'}
            - {key: http.method, value: GET}
          logs: []
          peer: null
```

SegmentB should only have one Tomcat entry span, but includes the Ref pointing to SegmentA.

SegmentB span list should like following
```yml
- segmentId: not null
  spans:
  -
   operationName: /httpclient-case/case/context-propagate
   parentSpanId: -1
   spanId: 0
   tags:
   - {key: url, value: 'http://127.0.0.1:8080/httpclient-case/case/context-propagate'}
   - {key: http.method, value: GET}
   logs: []
   startTime: nq 0
   endTime: nq 0
   spanLayer: Http
   isError: false
   spanType: Entry
   componentId: 1
   peer: null
   refs:
    - {parentEndpoint: /httpclient-case/case/httpclient, networkAddress: 'localhost:8080', refType: CrossProcess, parentSpanId: 1, parentTraceSegmentId: not null, parentServiceInstance: not null, parentService: not null, traceId: not null}
```

### The example Process of Writing Meter Expected Data

Expected data file, `expectedData.yaml`, include `MeterItems` part.

We are using the toolkit plugin to demonstrate how to write the expected data. When write the [meter plugin](Java-Plugin-Development-Guide.md#meter-plugin), the expected data file keeps the same.

There is one key point of testing
1. Build a meter and operate it.

Such as `Counter`:
```java
MeterFactory.counter("test_counter").tag("ck1", "cv1").build().increment(1d);
MeterFactory.histogram("test_histogram").tag("hk1", "hv1").steps(1d, 5d, 10d).build().addValue(2d);
```

```
+-------------+         +------------------+
|   Plugin    |         |    Agent core    |
|             |         |                  |
+-----|-------+         +---------|--------+
      |                           |         
      |                           |         
      |    Build or operate      +-+        
      +------------------------> |-|        
      |                          |-]
      |                          |-|        
      |                          |-|        
      |                          |-|
      |                          |-|        
      | <--------------------------|        
      |                          +-+        
      |                           |         
      |                           |         
      |                           |         
      |                           |         
      +                           +         
```

#### meterItems

By following the flow of the toolkit case,  there should be two meters created.
1. Meter `test_counter` created from `MeterFactory#counter`. Let's name it as `MeterA`.
1. Meter `test_histogram` created from `MeterFactory#histogram`. Let's name it as `MeterB`.

```yml
meterItems:
  - serviceName: toolkit-case
    meterSize: 2
```

They're showing two kinds of meter, MeterA has a single value, MeterB has a histogram value.

MeterA should like following, `counter` and `gauge` use the same data format.
```yaml
- meterId:
    name: test_counter
    tags:
      - {name: ck1, value: cv1}
  singleValue: gt 0
```

MeterB should like following.
```yaml
- meterId:
    name: test_histogram
    tags:
      - {name: hk1, value: hv1}
  histogramBuckets:
    - 0.0
    - 1.0
    - 5.0
    - 10.0
```

## Local Test and Pull Request To The Upstream

First of all, the test case project could be compiled successfully, with right project structure and be able to deploy.
The developer should test the start script could run in Linux/MacOS, and entryService/health services are able to provide
the response.

You could run test by using following commands

```bash
cd ${SKYWALKING_HOME}
bash ./test/plugin/run.sh -f ${scenario_name}
```

**Notice**，if codes in `./apm-sniffer` have been changed, no matter because your change or git update，
please recompile the `skywalking-agent`. Because the test framework will use the existing `skywalking-agent` folder,
rather than recompiling it every time.

Use `${SKYWALKING_HOME}/test/plugin/run.sh -h` to know more command options.

If the local test passed, then you could add it to `.github/workflows/plugins-test.<n>.yaml` file, which will drive the tests running on the GitHub Actions of official SkyWalking repository.
Based on your plugin's name, please add the test case into file `.github/workflows/plugins-test.<n>.yaml`, by alphabetical orders.

Every test case is a GitHub Actions Job. Please use the scenario directory name as the case `name`,
mostly you'll just need to decide which file (`plugins-test.<n>.yaml`) to add your test case, and simply put one line (as follows) in it, take the existed cases as examples.
You can run `python3 tools/select-group.py` to see which file contains the least cases and add your cases into it, in order to balance the running time of each group.

If a test case required to run in JDK 14 environment, please add you test case into file `plugins-jdk14-test.<n>.yaml`.

```yaml
jobs:
  PluginsTest:
    name: Plugin
    runs-on: ubuntu-latest
    timeout-minutes: 90
    strategy:
      fail-fast: true
      matrix:
        case:
          # ...
          - <your scenario test directory name>
          # ...
```
