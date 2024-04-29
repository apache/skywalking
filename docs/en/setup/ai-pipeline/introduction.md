# AI Pipeline

**Warning, this module is still in the ALPHA stage. This is not stable.**

Pattern Recognition, Machine Learning(ML) and Artificial Intelligence(AI) are common technology to identify patterns in data. 
This module provides a way to integrate these technologies in a standardized way about shipping the data from OAP kernel
to 3rd party.

From the industry practice, Pattern Recognition, Machine Learning(ML) and Artificial Intelligence(AI) are always overestimated,
they are good at many things but have to run in a clear context.

The ai-pipeline module is activated by default.

```yaml
ai-pipeline:
  selector: ${SW_AI_PIPELINE:default}
  default:
    uriRecognitionServerAddr: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_ADDR:}
    uriRecognitionServerPort: ${SW_AI_PIPELINE_URI_RECOGNITION_SERVER_PORT:17128}
```

## Supported Scenarios

* [**HTTP Restful URI recognition**](./http-restful-uri-pattern.md).
