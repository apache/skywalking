# Nginx monitoring
## Nginx performance from nginx-lua-prometheus
The [nginx-lua-prometheus](https://github.com/knyar/nginx-lua-prometheus) is a lua library that can be used with Nginx to collect metrics 
and expose them on a separate web page. 
To use this library, you will need Nginx with [lua-nginx-module](https://github.com/openresty/lua-nginx-module) or directly [OpenResty](https://openresty.org/).

SkyWalking leverages OpenTelemetry Collector to transfer the metrics to [OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).

### Data flow
1. [nginx-lua-prometheus](https://github.com/knyar/nginx-lua-prometheus) collects metrics from Nginx and expose them to an endpoint.
2. OpenTelemetry Collector fetches metrics from the endpoint expose above via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Collect Nginx metrics and expose the following four metrics by [nginx-lua-prometheus](https://github.com/knyar/nginx-lua-prometheus). For details on metrics definition, refer to [here](../../../../test/e2e-v2/cases/nginx/nginx.conf).
- histogram: nginx_http_latency
- gauge: nginx_http_connections
- counter: nginx_http_size_bytes
- counter: nginx_http_requests_total

2. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#docker). For details on Prometheus Receiver in OpenTelemetry Collector, refer to [here](../../../../test/e2e-v2/cases/nginx/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### Nginx Monitoring

SkyWalking observes the status, payload, and latency of the Nginx server, which is cataloged as a `LAYER: Nginx` `Service` in the OAP and instances would be recognized as `LAYER: Nginx` `instance`.

About `LAYER: Nginx` `endpoint`, it depends on how precision you want to monitor the nginx.
We do not recommend expose every request path metrics, because it will cause explosion of metrics endpoint data.

You can collect host metrics:
```
http {
  log_by_lua_block {
      metric_bytes:inc(tonumber(ngx.var.request_length), {"request", ngx.var.host})
      metric_bytes:inc(tonumber(ngx.var.bytes_send), {"response", ngx.var.host})
      metric_requests:inc(1, {ngx.var.status, ngx.var.host})
      metric_latency:observe(tonumber(ngx.var.request_time), {ngx.var.host})
  }
}
```
or grouped urls and upstream metrics:
```
upstream backend {
  server ip:port;
}

server {

  location /test {
    default_type application/json;
    return 200  '{"code": 200, "message": "success"}';
  
    log_by_lua_block {
      metric_bytes:inc(tonumber(ngx.var.request_length), {"request", "/test/**"})
      metric_bytes:inc(tonumber(ngx.var.bytes_send), {"response", "/test/**"})
      metric_requests:inc(1, {ngx.var.status, "/test/**"})
      metric_latency:observe(tonumber(ngx.var.request_time), {"/test/**"})
    }
  }
  
  location /test_upstream {
  
    proxy_pass http://backend;
  
    log_by_lua_block {
      metric_bytes:inc(tonumber(ngx.var.request_length), {"request", "upstream/backend"})
      metric_bytes:inc(tonumber(ngx.var.bytes_send), {"response", "upstream/backend"})
      metric_requests:inc(1, {ngx.var.status, "upstream/backend"})
      metric_latency:observe(tonumber(ngx.var.request_time), {"upstream/backend"})
    }
  }
}
```

#### Nginx Service Supported Metrics 
| Monitoring Panel        | Unit | Metric Name                                                                                   | Catalog | Description                                          | Data Source                    |
|-------------------------|------|-----------------------------------------------------------------------------------------------|---------|------------------------------------------------------|--------------------------------|
| HTTP Request Trend      |      | meter_nginx_service_http_requests                                                             | Service | The increment rate of HTTP requests                  | nginx-lua-prometheus           |
| HTTP Latency            | ms   | meter_nginx_service_http_latency                                                              | Service | The increment rate of the latency of HTTP requests   | nginx-lua-prometheus           |
| HTTP Bandwidth          | KB   | meter_nginx_service_bandwidth                                                                 | Service | The increment rate of the bandwidth of HTTP requests | nginx-lua-prometheus           |
| HTTP Connections        |      | meter_nginx_service_http_connections                                                          | Service | The avg number of the connections                    | nginx-lua-prometheus           |
| HTTP Status Trend       |      | meter_nginx_service_http_status                                                               | Service | The increment rate of the status of HTTP requests    | nginx-lua-prometheus           |
| HTTP Status 4xx Percent | %    | meter_nginx_service_http_4xx_requests_increment / meter_nginx_service_http_requests_increment | Service | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus           |
| HTTP Status 5xx Percent | %    | meter_nginx_service_http_5xx_requests_increment / meter_nginx_service_http_requests_increment | Service | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus           |

#### Nginx Instance Supported Metrics
| Monitoring Panel          | Unit | Metric Name                                                                                     | Catalog  | Description                                          | Data Source                    |
|---------------------------|------|-------------------------------------------------------------------------------------------------|----------|------------------------------------------------------|--------------------------------|
| HTTP Request Trend        |      | meter_nginx_instance_http_requests                                                              | Instance | The increment rate of HTTP requests                  | nginx-lua-prometheus           |
| HTTP Latency              | ms   | meter_nginx_instance_http_latency                                                               | Instance | The increment rate of the latency of HTTP requests   | nginx-lua-prometheus           |
| HTTP Bandwidth            | KB   | meter_nginx_instance_bandwidth                                                                  | Instance | The increment rate of the bandwidth of HTTP requests | nginx-lua-prometheus           |
| HTTP Connections          |      | meter_nginx_instance_http_connections                                                           | Instance | The avg number of the connections                    | nginx-lua-prometheus           |
| HTTP Status Trend         |      | meter_nginx_instance_http_status                                                                | Instance | The increment rate of the status of HTTP requests    | nginx-lua-prometheus           |
| HTTP Status 4xx Percent   | %    | meter_nginx_instance_http_4xx_requests_increment / meter_nginx_instance_http_requests_increment | Instance | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus           |
| HTTP Status 5xx Percent   | %    | meter_nginx_instance_http_5xx_requests_increment / meter_nginx_instance_http_requests_increment | Instance | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus           |

#### Nginx Endpoint Supported Metrics
| Monitoring Panel        | Unit | Metric Name                                                                                     | Catalog  | Description                                          | Data Source          |
|-------------------------|------|-------------------------------------------------------------------------------------------------|----------|------------------------------------------------------|----------------------|
| HTTP Request Trend      |      | meter_nginx_endpoint_http_requests                                                              | Endpoint | The increment rate of HTTP requests                  | nginx-lua-prometheus |
| HTTP Latency            | ms   | meter_nginx_endpoint_http_latency                                                               | Endpoint | The increment rate of the latency of HTTP requests   | nginx-lua-prometheus |
| HTTP Bandwidth          | KB   | meter_nginx_endpoint_bandwidth                                                                  | Endpoint | The increment rate of the bandwidth of HTTP requests | nginx-lua-prometheus |
| HTTP Status Trend       |      | meter_nginx_endpoint_http_status                                                                | Endpoint | The increment rate of the status of HTTP requests    | nginx-lua-prometheus |
| HTTP Status 4xx Percent | %    | meter_nginx_endpoint_http_4xx_requests_increment / meter_nginx_endpoint_http_requests_increment | Endpoint | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus |
| HTTP Status 5xx Percent | %    | meter_nginx_endpoint_http_5xx_requests_increment / meter_nginx_endpoint_http_requests_increment | Endpoint | The percentage of 4xx status of HTTP requests        | nginx-lua-prometheus |

### Customizations
You can customize your own metrics/expression/dashboard panel.

The metrics definition and expression rules are found in `/config/otel-rules/nginx-service.yaml, /config/otel-rules/nginx-instance.yaml, /config/otel-rules/nginx-endpoint.yaml`.

The Nginx dashboard panel configurations are found in `/config/ui-initialized-templates/nginx`.

## Collect nginx access and error log
SkyWalking leverages [fluentbit](https://fluentbit.io/) or other log agents for collecting access log and error log of Nginx.

### Data flow
1. fluentbit agent collects access log and error log from Nginx.
2. fluentbit agent sends data to SkyWalking OAP Server using native meter APIs via HTTP.
3. The SkyWalking OAP Server parses the expression with [LAL](../../concepts-and-designs/lal.md) to parse/extract and store the results.

### Set up
1. Install [fluentbit](https://docs.fluentbit.io/manual/installation/getting-started-with-fluent-bit).
2. Config fluent bit with fluent-bit.conf, refer to [here](../../../../test/e2e-v2/cases/nginx/fluent-bit.conf).

### Error Log Monitoring
Error Log monitoring provides monitoring of the error.log of the Nginx server.

#### Supported Metrics
| Monitoring Panel         | Metric Name                          | Catalog  | Description                               | Data Source |
|--------------------------|--------------------------------------|----------|-------------------------------------------|-------------|
| Service Error Log Count  | meter_nginx_service_error_log_count  | Service  | The count of log level of nginx error.log | fluent bit  |
| Instance Error Log Count | meter_nginx_instance_error_log_count | Instance | The count of log level of nginx error.log | fluent bit  |

### Customizations
You can customize your own metrics/expression/dashboard panel.

The log collect and analyse rules are found in `/config/lal/nginx.yaml`, `/config/log-mal-rules/nginx.yaml`.

The Nginx dashboard panel configurations are found in `/config/ui-initialized-templates/nginx`.