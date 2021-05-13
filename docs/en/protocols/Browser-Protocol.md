# Browser Protocol

Browser protocol describes the data format between [skywalking-client-js](https://github.com/apache/skywalking-client-js) and the backend.

## Overview

Browser protocol is defined and provided in [gRPC format](https://github.com/apache/skywalking-data-collect-protocol/blob/master/browser/BrowserPerf.proto),
and also implemented in [HTTP 1.1](Browser-HTTP-API-Protocol.md)

### Send performance data and error logs

You can send performance data and error logs using the following services:

1. `BrowserPerfService#collectPerfData` for performance data format.
1. `BrowserPerfService#collectErrorLogs` for error log format.

For error log format, note that:

1. `BrowserErrorLog#uniqueId` should be unique in all distributed environments.
