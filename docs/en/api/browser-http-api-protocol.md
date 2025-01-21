# HTTP API Protocol

HTTP API Protocol defines the API data format, including API request and response data format.
They use the HTTP1.1 wrapper of the official [SkyWalking Browser Protocol](browser-protocol.md). Read it for more details.

## Performance Data Report

Detailed information about data format can be found in [BrowserPerf.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/browser/BrowserPerf.proto).

### POST http://localhost:12800/browser/perfData

Send a performance data object in JSON format.

Input:

```json
{
  "service": "web",
  "serviceVersion": "v0.0.1",
  "pagePath": "/index.html",
  "redirectTime": 10,
  "dnsTime": 10,
  "ttfbTime": 10,
  "tcpTime": 10,
  "transTime": 10,
  "domAnalysisTime": 10,
  "fptTime": 10,
  "domReadyTime": 10,
  "loadPageTime": 10,
  "resTime": 10,
  "sslTime": 10,
  "ttlTime": 10,
  "firstPackTime": 10,
}
```

OutPut:

HTTP Status: 204

### POST http://localhost:12800/browser/perfData/webVitals

Send a performance data object in JSON format. Since client-js 1.0.0, the following attached metrics are reported.

Input:

```json
{
  "service": "web",
  "serviceVersion": "v0.0.1",
  "pagePath": "/index.html",
  "fmpTime": 10,
  "clsTime": 10,
  "lcpTime": 10,
}
```

OutPut:

HTTP Status: 204

### POST http://localhost:12800/browser/perfData/webInteractions

Send a performance data object in JSON format. Since client-js 1.0.0, the following attached metrics are reported.

Input:

```json
[
  {
    "service": "web",
    "serviceVersion": "v0.0.1",
    "pagePath": "/index.html",
    "inpTime": 10,
  }
]
```

OutPut:

HTTP Status: 204

### POST http://localhost:12800/browser/perfData/resources

Send a static resources data object in JSON format. Since client-js 1.0.0, the following attached metrics are reported.

Input:

```json
[
  {
    "service": "web",
    "serviceVersion": "v0.0.1",
    "pagePath": "/index.html",
    "name": "vue.js",
    "duration": 600,
    "size": 100000,
    "protocol": "h2",
    "type": "script",
  }
]
```

OutPut:

HTTP Status: 204

## Error Log Report

Detailed information about data format can be found in [BrowserPerf.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/browser/BrowserPerf.proto).

### POST http://localhost:12800/browser/errorLogs

Send an error log object list in JSON format.

Input:

```json
[
    {
        "uniqueId": "55ec6178-3fb7-43ef-899c-a26944407b01",
        "service": "web",
        "serviceVersion": "v0.0.1",
        "pagePath": "/index.html",
        "category": "ajax",
        "message": "error",
        "line": 1,
        "col": 1,
        "stack": "error",
        "errorUrl": "/index.html"
    },
    {
        "uniqueId": "55ec6178-3fb7-43ef-899c-a26944407b02",
        "service": "web",
        "serviceVersion": "v0.0.1",
        "pagePath": "/index.html",
        "category": "ajax",
        "message": "error",
        "line": 1,
        "col": 1,
        "stack": "error",
        "errorUrl": "/index.html"
    }
]
```

OutPut:

HTTP Status: 204

### POST http://localhost:12800/browser/errorLog

Send a single error log object in JSON format.

Input:

```json
{
  "uniqueId": "55ec6178-3fb7-43ef-899c-a26944407b01",
  "service": "web",
  "serviceVersion": "v0.0.1",
  "pagePath": "/index.html",
  "category": "ajax",    
  "message": "error",
  "line": 1,
  "col": 1,
  "stack": "error",
  "errorUrl": "/index.html"
}
```

OutPut:

HTTP Status: 204
