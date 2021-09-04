# Introduction to UI
The SkyWalking official UI provides the default and powerful visualization capabilities for SkyWalking to observe distributed
clusters.

Watch the latest introduction video on Youtube:

[![RocketBot UI](https://img.youtube.com/vi/mfKaToAKl7k/0.jpg)](http://www.youtube.com/watch?v=mfKaToAKl7k)

The SkyWalking dashboard includes these parts:

<img src="https://skywalking.apache.org/ui-doc/7.0.0/dashboard.png"/>

1. **Feature Tab Selector Zone**. The key features are listed there. More details will be introduced below.
1. **Reload Zone**. It controls the reload mechanism, including the option to reload periodically or manually.
1. **Time Selector Zone**. It controls the timezone and time range, and comes with a Chinese/English language switch button. By default, the UI
uses the language setting of the browser. We also welcome translation contributions to extend our reach into more languages.

## Dashboard
The dashboard provides metrics of services, service instances, and endpoints. Here's a quick terminology guide on metrics:
* **Throughput CPM**: Represents calls per minute.
* **Apdex score**: See [Apdex on Wiki](https://en.wikipedia.org/wiki/Apdex).
* **Response Time Percentile**: Includes `p99`, `p95`, `p90`, `p75`, and `p50`. See [percentile on Wiki](https://en.wikipedia.org/wiki/Percentile).
* **SLA**: Represents the success rate. For HTTP, the response status code is default to 200.

The Service, Instance and Dashboard selectors can be reloaded manually, so it's not necessary to always reload the whole page. Note that the **Reload Zone** does not reload these selectors.

<img src="https://skywalking.apache.org/ui-doc/7.0.0/dashboard-reload.png"/>

Two default dashboards are provided to visualize the metrics of service and database.

<img src="https://skywalking.apache.org/ui-doc/7.0.0/dashboard-default.png"/>

Click the `Lock` button on the left of the `Service/Instance/Endpoint Reload` button to customize your dashboard.

### Custom Dashboard
Users may customize their dashboards. The default dashboards are provided in the default templates located in the `/ui-initialized-templates` folders.

The template file must end with `.yml` or `.yaml`. It follows this format:
```yaml
templates:
  - name: template name # The unique name
    # The type includes DASHBOARD, TOPOLOGY_INSTANCE, TOPOLOGY_ENDPOINT.
    # DASHBOARD type templates could have multiple definitions, by using different names.
    # TOPOLOGY_INSTANCE, TOPOLOGY_ENDPOINT type templates should be defined once, 
    # as they are used in the topology page only.
    type: "DASHBOARD" 
    # Custom the dashboard or create a new one on the UI, set the metrics as you like in the edit mode.
    # Then, you could export this configuration through the page and add it here.
    configuration: |-
      [
        {
          "name":"Spring Sleuth",
          "type":"service",
          "children":[
            {
              "name":"Sleuth",
              "children": [{
                "width": "3",
                "title": "HTTP Request",
                "height": "200",
                "entityType": "ServiceInstance",
                "independentSelector": false,
                "metricType": "REGULAR_VALUE",
                "metricName": "meter_http_server_requests_count",
                "queryMetricType": "readMetricsValues",
                "chartType": "ChartLine",
                "unit": "Count"
              }
              ...
              ]
            }
          ]
      }
      ]
    # Activated means this templates added into the UI page automatically.
    # False means providing a basic template, user needs to add it manually on the page.
    activated: false
    # True means wouldn't show up on the dashboard. Only keeps the definition in the storage.
    disabled: false
```

**NOTE**: UI initialized templates would only be initialized if no template in the storage has the same name.
Check the entity named `ui_template` in your storage.

## Topology
A topology map shows the relationship between services and instances with metrics.

<img src="https://skywalking.apache.org/ui-doc/8.4.0/topology.png"/>

Global topology is shown by default, which means that all services are included.
* **Service Selector** provides two-level selectors, service group lists, and service name lists. The group name is separated from 
the service name if it follows the `<group name>::<logic name>` format. Topology maps are available for single group, single service, 
or global (where all services are included).
* **Custom Group** allows you to create sub-topologies for a service group.
* **Service Deep Dive** opens when you click on any service. The honeycomb could carry out metrics, trace, and alarm query of the selected service.
* **Service Relationship Metrics** provides the metrics of service RPC interactions and the instances of these two services.

## Trace Query
Since SkyWalking provides distributed agents, trace query is a key feature.

<img src="https://skywalking.apache.org/ui-doc/7.0.0/trace.png"/>

* **Trace Segment List** is not the same as a trace list. Every trace has several segments belonging to different services. If you start a query by all services or by trace IDs, different segments with the same trace ID may be listed there.
* **Span** can be clicked. The details of each span will pop up on the left.
* **Trace Views** provides three typical and different usage views to visualize the trace. 

## Profile
Profile is an interactive feature. It provides method-level performance diagnoses. 

To start profile analysis, you need to create a profile task:

<img src="https://skywalking.apache.org/ui-doc/7.0.0/profile-create.png" width="440px"/>

1. Select the specific service. 
1. Set the Endpoint Name. This endpoint name is typically the operation name of the first span. Find this on the trace 
segment list view.
1. Monitor Time could start right now or from any given future time.
1. Monitor Duration defines the observation time window to find the suitable request to conduct performance analysis.
Even though the profile has a very limited performance impact on the target system, it still amounts to an additional load. Setting this duration allows you to control the impact.
1. Min Duration Threshold provides a filter mechanism. If a request of the given endpoint responds quickly, it will not be profiled. This ensures that the profiled data is the expected one.
1. Max Sampling Count gives the maximum dataset to be collected by the agent. It helps reduce memory and network load.
1. An implicit condition is that **at any moment, SkyWalking only accepts one profile task for each service**.
1. Individual agents may have different settings to control or limit this feature. Read document setup for more details.
1. Not all SkyWalking ecosystem agents support this feature. Java agent from version 7.0.0 supports this by default.

Once the profile is done, the profiled trace segments would show up, and you could request to analyze any span.
Typically, we analyze spans with long self duration. If a span and its children both have long duration, you could set the analysis boundaries by choosing to `Include Children` or `Exclude Children`. 

Choose the appropriate span, and click `Analyze`. You will see the stack-based analysis results. The slowest methods are highlighted.

<img src="https://skywalking.apache.org/ui-doc/7.0.0/profile-result.png"/>

### Advanced features
1. Since version 7.1.0, the profiled trace automatically collects the HTTP request parameters for Tomcat and SpringMVC Controller.

## Log
Since version 8.3.0, SkyWalking has provided log query for browser monitoring. Use [Apache SkyWalking Client JS](https://github.com/apache/skywalking-client-js) agent to collect metrics and error logs.

Since version 8.5.0, SkyWalking supports collecting logs through its native agents and third party agents (such as Fluentd and Filebeat). 
See [Log Analyzer Document](../setup/backend/log-analyzer.md) for more details.

<img src="https://skywalking.apache.org/ui-doc/8.3.0/log.png"/>

## Alarm
The alarm page lists all triggered alarms. See backend setup documentation to learn how to set up alarm rules or integrate
with third party systems.
