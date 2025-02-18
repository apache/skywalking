# AI Pipeline

**Warning, this module is still in the ALPHA stage. This is not stable.**

Pattern Recognition, Machine Learning(ML) and Artificial Intelligence(AI) are common technology to identify patterns in data.
From the industry practice, these three are always overestimated for the marketing interests,
they are good at many things but have to run in a clear context.
Hence, SkyWalking OAP AI pipeline features are designed for very specific solutions and scenarios with at 
least one recommended (remote) implementations for the integration。

The ai-pipeline module is activated by default for the latest release. Make sure you have these configurations when upgrade
from a previous version.

```yaml
ai-pipeline:
  selector: ${SW_AI_PIPELINE:default}
  default:
    # HTTP Restful URI recognition service address configurations
    uriRecognitionServerAddr: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_ADDR:}
    uriRecognitionServerPort: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_PORT:17128}
    # Metrics Baseline Calculation service address configurations
    baselineServerAddr: ${SW_API_PIPELINE_BASELINE_SERVICE_HOST:}
    baselineServerPort: ${SW_API_PIPELINE_BASELINE_SERVICE_PORT:18080}
```

We supported the following AI features:

* [**HTTP Restful URI recognition**](./http-restful-uri-pattern.md).
* [**Metrics Baseline Calculation and Alerting**](./metrics-baseline-integration.md).
