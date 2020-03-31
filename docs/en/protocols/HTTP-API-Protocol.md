# HTTP API Protocol

HTTP API Protocol defines the API data format, including api request and response data format.

### Do register

Detail information about data format can be found in  [Register service](https://github.com/apache/skywalking-data-collect-protocol/tree/master/register/Register.proto).
And register steps followings [SkyWalking Trace Data Protocol v2](Trace-Data-Protocol-v3.md).

- Service Register

> POST http://localhost:12800/v2/service/register

Input:

```json
{
  "services": [
    {
      "type": "normal",
      "serviceName": "Service Name"
    }
  ]
}
```

Output JSON Array:

```json
[
    {
        "key": "Service Name",
        "value": 2
    }
]
```

- Service instance Register

> POST http://localhost:12800/v2/instance/register

Input:

```json
{
  "instances": [
    {
      "time": 1582428603392,
      "instanceUUID": "NAME:Service Instance Name",
      "properties": [
        {
          "key": "language",
          "value": "Lua"
        }
      ],
      "serviceId": 2
    }
  ]
}
```

OutPut:

```json
[
    {
        "key": "NAME:Service Instance Name",
        "value": 0
    }
]
```

- Service instance heartbeat

> POST http://localhost:12800/v2/instance/heartbeat

Input:

```json
{
  "serviceInstanceId":20,
  "time": 1582428603392,
  "serviceInstanceUUID":"NAME:Service Instance Name"
}
```

OutPut:

```json
{}
```
If your instance does not exist, you need to clean your local service instance metadata in your application and re-do register:

```json
{
    "commands": [
        {
            "command": "ServiceMetadataReset",
            "args": [
                {
                    "key": "SerialNumber",
                    "value": "44bd2664-03c7-46bc-8652-52fcde0e7699"
                }
            ]
        }
    ]
}
```  

## Trace Report

### POST http://localhost:12800/v2/segments

Input:

```json
{
  "spans": [
    {
      "operationName": "/tier2/lb",
      "startTime": 1582461179910,
      "tags": [],
      "endTime": 1582461179922,
      "spanType": "Exit",
      "logs":[],
      "spanId": 1,
      "isError": false,
      "parentSpanId": 0,
      "componentId": 6000,
      "peer": "User Service Name-nginx:upstream_ip:port",
      "spanLayer": "Http"
    },
    {
      "operationName": "/tier2/lb",
      "startTime": 1582461179910,
      "tags": [
        {
          "key": "http.method",
          "value": "GET"
        },
        {
          "key": "http.params",
          "value": "http://127.0.0.1/tier2/lb"
        }
      ],
      "endTime": 1582461179922,
      "spanType": "Entry",
      "logs": [],
      "spanId": 0,
      "isError": false,
      "parentSpanId": -1,
      "componentId": 6000,
      "refs": [
        {
          "parentTraceSegmentId": {
            "idParts": [
              1582461179038,
              794206293,
              69887
            ]
          },
          "parentEndpointId": 0,
          "entryEndpointId": 0,
          "parentServiceInstanceId": 1,
          "parentEndpoint": "/ingress",
          "networkAddress": "#User Service Name-nginx:upstream_ip:port",
          "parentSpanId": 1,
          "entryServiceInstanceId": 1,
          "networkAddressId": 0,
          "entryEndpoint": "/ingress"
        }
      ],
      "spanLayer": "Http"
    }
  ],
  "serviceInstanceId": 1,
  "serviceId": 1,
  "traceSegmentId": {
    "idParts": [
      1582461179044,
      794206293,
      69887
    ]
  },
  "globalTraceIds": [
    {
      "idParts": [
        1582461179038,
        794206293,
        69887
      ]
    }
  ]
}
```
 OutPut:
 
 ```json

```