# Plugin automatic test framework

Plugin test framework is designed for verifying the plugins' function and compatible status. As there are dozens of plugins and
hundreds of versions need to be verified, it is impossible to do manually.
The test framework uses container based tech stack, requires a set of real services with agent installed, then the test mock
OAP backend is running to check the segments data sent from agents.

Every plugin maintained in the main repo requires corresponding test cases, also matching the versions in the supported list doc.

## Environment Requirements

1. MacOS/Linux
2. jdk 8+
3. Docker
4. Docker Compose

## Case Base Image Introduction

The test framework provides `JVM-container` and `Tomcat-container` base images. You could choose the suitable one for your test case, if either is suitable, **recommend choose `JVM-container`**.

### JVM-container Image Introduction

[JVM-container](../../../test/plugin/containers/jvm-container) uses `openjdk:8` as the base image.
The test case project is required to be packaged as `project-name.zip`, including `startup.sh` and uber jar, by using `mvn clean package`.

Take the following test projects as good examples
* [sofarpc-scenario](../../../test/plugin/scenarios/sofarpc-scenario) as a single project case.
* [webflux-scenario](../../../test/plugin/scenarios/webflux-scenario) as a case including multiple projects.

### Tomcat-container Image Introduction

[Tomcat-container](../../../test/plugin/containers/tomcat-container) uses `tomcat:8.5.42-jdk8-openjdk` as the base image.
The test case project is required to be packaged as `project-name.war` by using `mvn package`.

Take the following test project as a good example
* [spring-4.3.x-scenario](https://github.com/apache/skywalking/tree/master/test/plugin/scenarios/spring-4.3.x-scenario)


## Test project hierarchical structure
The test case is an independent maven project, and it is required to be packaged as a war tar ball or zip file, depends 
on the chosen base image. Also, two external accessible endpoints, mostly two URLs, are required.

All test case codes should be in `org.apache.skywalking.apm.testcase.*` package, unless there are some codes expected being instrumented,
then the classes could be in `test.org.apache.skywalking.apm.testcase.*` package.

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
`configuration.yml` | Declare the basic case inform, including, case name, entrance endpoints, mode, dependencies.
`expectedData.yaml` | Describe the expected segmentItems.
`support-version.list` | List the target versions for this case
`startup.sh` |`JVM-container` only, don't need this when use`Tomcat-container`

`*` support-version.list format requires every line for a single version. Could use `#` to comment out this version.

### configuration.yml

| Field | description
| --- | ---
| type | Image type, options, `jvm` or `tomcat`. Required.
| entryService | The entrance endpoint(URL) for test case access. Required. (HTTP Method: GET)
| healthCheck | The health check endpoint(URL) for test case access. Required. (HTTP Method: HEAD)
| startScript | Path of start up script. Required in `type: jvm` only.
| framework | Case name.
| runningMode | Running mode whether with the optional plugin, options, `default`(default), `with_optional`, `with_bootstrap`
| withPlugins | Plugin selector rule. eg:`apm-spring-annotation-plugin-*.jar`. Required when `runningMode=with_optional` or `runningMode=with_bootstrap`.
| environment | Same as `docker-compose#environment`.
| depends_on | Same as `docker-compose#depends_on`.
| dependencies | Same as `docker-compose#services`, `image、links、hostname、environment、depends_on` are supported.

**Notice:, `docker-compose` active only when `dependencies` is only blank.**

**runningMode** option description.

| Option | description
| --- | ---
| default | Active all plugins in `plugin` folder like the official distribution agent. 
| with_optional | Active `default` and plugins in `optional-plugin` by the give selector.
| with_bootstrap | Active `default` and plugins in `bootstrap-plugin` by the give selector.

with_optional/with_bootstrap supports multiple selectors, separated by `;`.

**File Format**

```
type:
entryService:
healthCheck:
startScript:
framework:
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

* dependencies supports docker compose `healthcheck`. But the format is a little difference. We need `-` as the start of every config item,
and describe it as a string line.

Such as in official doc, the health check is
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost"]
  interval: 1m30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

The here, you should write as
```yaml
healthcheck:
  - 'test: ["CMD", "curl", "-f", "http://localhost"]'
  - "interval: 1m30s"
  - "timeout: 10s"
  - "retries: 3"
  - "start_period: 40s"
```

In some cases, the dependency service, mostly 3rd party server like SolrJ server, is required to keep the same version
as client lib version, which defined as `${test.framework.version}` in pom. Could use `${CASE_SERVER_IMAGE_VERSION}`
as the version number, it will be changed in the test for every version.

> Don't support resource related configurations, such as volumes, ports and ulimits. Because in test scenarios, 
> don't need mapping any port to the host VM, or mount any folder.

**Take following test cases as examples**
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

**Segment verify description format**
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
| segmentId | trace ID.
| spans | segment span list. Follow the next section to see how to describe every span.

**Span verify description format**

**Notice**: The order of span list should follow the order of the span finish time.

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
| parentSpanId | Parent span id. **Notice**: The parent span id of the first span should be -1. 
| spanId | Span Id. **Notice**, start from 0. 
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

### The example Process of Writing Expected Data

Expected data file, `expectedData.yaml`, include `SegmentItems`.

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

If the local test passed, then you could add it to `.github/workflows/plugins-test.<n>.yaml` file, which will drive the tests running on the Github Actions of official SkyWalking repository.
Based on your plugin's name, please add the test case into file `.github/workflows/plugins-test.<n>.yaml`, by alphabetical orders.

Every test case is a Github Actions Job. Please use the `<scenario name> + <version range> + (<supported version count>)` as the Job `title`, and the scenario directory as the Job `name`,
mostly you'll just need to decide which file (`plugins-test.<n>.yaml`) to add your test case, and simply put one line (as follows) in it, take the existed cases as examples.

```yaml
jobs:
  PluginsTest:
    name: Plugin
    runs-on: ubuntu-18.04
    timeout-minutes: 90
    strategy:
      fail-fast: true
      matrix:
        case:
          # ...
          - { name: '<your case name>', title: '<PluginName, i.e. Spring> (<Supported Version Count, i.e 12>)' } # <<== insert one line by alphabetical orders
          # ...
```
