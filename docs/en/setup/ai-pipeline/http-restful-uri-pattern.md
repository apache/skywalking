# HTTP Restful URI recognition

As introduced in the [Group Parameterized Endpoints](../backend/endpoint-grouping-rules.md) doc, HTTP Restful URIs are identified
as endpoints. With some additional rules, we can identify the parameters in the URI and group the endpoints in case of annoying
and huge size of endpoint candidates with low value of the metrics.

In the ML/AI specific fields, decision trees or neural networks can be trained on labeled URI data to automatically 
recognize and classify different URI patterns, as well as many other ways.

In this pipeline, OAP has the capabilities to cache the URI candidates with occurrence count,
and push the data to 3rd party for further analysis. Then OAP would pull the analyzed results for
processing the further telemetry traffic.

## Make sure the `ai-pipeline` module activated.

The ai-pipeline module is activated by default for the latest release. Make sure you have these configurations when upgrade
from a previous version.

```yaml
ai-pipeline:
  selector: ${SW_AI_PIPELINE:default}
  default:
    uriRecognitionServerAddr: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_ADDR:}
    uriRecognitionServerPort: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_PORT:17128}
```

## Set up OAP to connect remote URI recognition server
`uriRecognitionServerAddr` and `uriRecognitionServerPort` are the configurations to set up the remote URI recognition server.

The URI recognition server is a gRPC server, which is defined in [URIRecognition.proto](../../../../oap-server/ai-pipeline/src/main/proto/ai_http_uri_recognition.proto).

```protobuf
service HttpUriRecognitionService {
    // Sync for the pattern recognition dictionary.
    rpc fetchAllPatterns(HttpUriRecognitionSyncRequest) returns (HttpUriRecognitionResponse) {}
    // Feed new raw data and matched patterns to the AI-server.
    rpc feedRawData(HttpUriRecognitionRequest) returns (google.protobuf.Empty) {}
}
```

- fetchAllPatterns service

fetchAllPatterns is up and running in 1 minute period from every OAP to fetch all recognized patterns from the remote server.

- feedRawData service

feedRawData is running in 25-30 minutes period to push the raw data to the remote server for training.

## Configurations

- `core/maxHttpUrisNumberPerService` The max number of HTTP URIs per service for further URI pattern recognition.
- `core/syncPeriodHttpUriRecognitionPattern` The period of HTTP URI pattern recognition(feedRawData). Unit is second, 10s by default.
- `core/trainingPeriodHttpUriRecognitionPattern` The training period of HTTP URI pattern recognition(fetchAllPatterns). Unit is second, 60s by default.

## Optional Server Implementation

### R3

[RESTful Pattern Recognition(R3)](https://github.com/SkyAPM/r3) is an Apache 2.0 licensed implementation for the URI
recognition, and natively supports `URIRecognition.proto` defined in OAP.
