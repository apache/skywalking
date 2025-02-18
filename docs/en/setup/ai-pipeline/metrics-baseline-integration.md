# Metrics Baseline Calculation and Alerting

Metrics baseline calculation and alerting is a feature that calculates the baseline of metrics data and feed for the
alarm engine as additional metrics to setup rules for alerting.
[Alarm docs](../backend/backend-alarm.md#use-the-baseline-predicted-value-to-trigger-the-alarm) has more details about
how to use the baseline,  and further about MQE usages of the baseline values.

SkyAPM community provides a default implementation [SkyAPM/SkyPredictor](https://github.com/SkyAPM/SkyPredictor).
It has complete support for the baseline calculation by following SkyWalking's metrics data model through GraphQL, and
feed baseline data back to the OAP server through the following gRPC service per SkyWalking requirement..

```protobuf
service AlarmBaselineService {
  // Query the supported metrics names.
  rpc querySupportedMetricsNames(google.protobuf.Empty) returns (AlarmBaselineMetricsNames);
  // Query the predicted metrics of the given service.
  rpc queryPredictedMetrics(AlarmBaselineRequest) returns (AlarmBaselineResponse);
}
```

You could find the protocol definition
in [AlarmBaseline.proto](../../../../oap-server/ai-pipeline/src/main/proto/baseline.proto).