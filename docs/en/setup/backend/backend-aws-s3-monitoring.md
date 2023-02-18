# AWS Cloud S3 monitoring
Amazon Simple Storage Service (Amazon S3) is an object storage service. SkyWalking leverages [AWS Kinesis Data Firehose receiver](./aws-firehose-receiver.md) to transfer the CloudWatch metrics of s3 to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. AWS CloudWatch collect metrics for S3, refer to [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html)
2. [CloudWatch metric streams](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Metric-Streams.html) stream CloudWatch metrics of S3 to AWS Kinesis Data Firehose
3. AWS Kinesis Data Firehose delivery metrics to [AWS Kinesis Data Firehose receiver](./aws-firehose-receiver.md) through the HTTP endpoint

### Set up
1. Create CloudWatch metrics configuration for S3, refer to [S3 metrics configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/metrics-configurations.html)
2. Create an Amazon Kinesis Data Firehose Delivery Stream, and set [AWS Kinesis Data Firehose receiver](./aws-firehose-receiver.md)'s address as HTTP Destination, refer to [Create Delivery Stream](https://docs.aws.amazon.com/firehose/latest/dev/basic-create.html)
3. Create CloudWatch metric stream, and select the Firehose Delivery Stream which has been created above, set `Select namespaces` to `AWS/S3`, `Select output format` to `OpenTelemetry 0.7`. refer to [CloudWatch Metric Streams](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Metric-Streams.html)

### S3 Monitoring

SkyWalking observes CloudWatch metrics of the S3 bucket, which is cataloged as a `LAYER: AWS_S3` `Service` in the OAP.

#### Supported Metrics

| Monitoring Panel           | Unit  | Metric Name                 | Catalog        | Description                                                                                                                                | Data Source                                                                                                       |
|----------------------------|-------|-----------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| 4xx Errors                 | count | aws_s3_4xx                  | Service        | The number of HTTP 4xx client error status code requests made to the S3 bucket                                                             | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| 5xx Errors                 | count | aws_s3_5xx                  | Service        | The number of HTTP 5xx client error status code requests made to the S3 bucket                                                             | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Downloaded                 | bytes | aws_s3_downloaded_bytes     | Service        | The number of bytes downloaded for requests made to an Amazon S3 bucket                                                                    | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Uploaded                   | bytes | aws_s3_uploaded_bytes       | Service        | The number of bytes uploaded for requests made to an Amazon S3 bucket                                                                      | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Request Average Latency    | bytes | aws_s3_request_latency      | Service        | The average of elapsed per-request time from the first byte received to the last byte sent to an Amazon S3 bucket                          | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| First Byte Average Latency | bytes | aws_s3_request_latency      | Service        | The average of per-request time from the complete request being received by an Amazon S3 bucket to when the response starts to be returned | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| All Requests               | bytes | aws_s3_delete_requests      | Service        | The number of HTTP All requests made for objects in an Amazon S3 bucket                                                                    | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Get Requests               | bytes | aws_s3_delete_requests      | Service        | The number of HTTP Get requests made for objects in an Amazon S3 bucket                                                                    | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Put Requests               | bytes | aws_s3_delete_requests      | Service        | The number of HTTP PUT requests made for objects in an Amazon S3 bucket                                                                    | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |
| Delete Requests            | bytes | aws_s3_delete_requests      | Service        | The number of HTTP Delete requests made for objects in an Amazon S3 bucket                                                                 | [S3 monitoring with CloudWatch](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cloudwatch-monitoring.html) |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/aws-s3/`.
The AWS Cloud EKS dashboard panel configurations are found in `/config/ui-initialized-templates/aws_s3`.
