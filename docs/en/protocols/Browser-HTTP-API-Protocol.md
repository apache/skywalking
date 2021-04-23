# HTTP API Protocol

HTTP API Protocol defines the API data format, including api request and response data format.
They use the HTTP1.1 wrapper of the official [SkyWalking Browser Protocol](Browser-Protocol.md). Read it for more details.

## Performance Data Report

Detail information about data format can be found in [BrowserPerf.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/browser/BrowserPerf.proto).

### POST http://localhost:12800/browser/perfData

Send a performance data object with JSON format.

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
  "fmpTime": 10
}
```

OutPut:

Http Status: 204

## Error Log Report

Detail information about data format can be found in [BrowserPerf.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/browser/BrowserPerf.proto).

### POST http://localhost:12800/browser/errorLogs

Send an error log object list with JSON format.

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

Http Status: 204

### POST http://localhost:12800/browser/errorLog

Send a single error log object with JSON format.

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

Http Status: 204
