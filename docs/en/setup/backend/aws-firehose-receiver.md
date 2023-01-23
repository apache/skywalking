# AWS Firehose receiver

AWS Firehose receiver listens on `0.0.0.0:12801` by default, and provides an HTTP Endpoint `/aws/firehose/metrics` that follows [Amazon Kinesis Data Firehose Delivery Stream HTTP Endpoint Delivery Specifications](https://docs.aws.amazon.com/firehose/latest/dev/httpdeliveryrequestresponse.html)
You could leverage the receiver to collect [AWS CloudWatch metrics](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/working_with_metrics.html), and analysis it through [MAL](../../concepts-and-designs/mal.md) as the receiver bases on [OpenTelemetry receiver](./opentelemetry-receiver.md)

## Setup(S3 example)

1. Create CloudWatch metrics configuration for S3 (refer to [S3 CloudWatch metrics](https://docs.aws.amazon.com/AmazonS3/latest/userguide/configure-request-metrics-bucket.html)) 
2. Stream CloudWatch metrics to AWS Kinesis Data Firehose delivery stream by [CloudWatch metrics stream](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-metric-streams-setup-datalake.html)
3. Specify AWS Kinesis Data Firehose delivery stream HTTP Endpoint (refer to [Choose HTTP Endpoint for Your Destination](https://docs.aws.amazon.com/firehose/latest/dev/create-destination.html#create-destination-http))

Usually, the [AWS CloudWatch metrics](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/working_with_metrics.html) process flow with OAP is as follows:
```
CloudWatch metrics with S3 -->  CloudWatch Metric Stream (OpenTelemetry formart) --> Kinesis Data Firehose Delivery Stream --> AWS Firehose receiver(OAP) --> OpenTelemetry receiver(OAP)
```

## Notice

1. Only OpenTelemetry format is supported (refer to [Metric streams output formats](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-metric-streams-formats.html))
2. Only HTTPS could be accepted, you could directly enable TLS and set the receiver to listen 443, or put the receiver behind a gateway with HTTPS (refer to [Amazon Kinesis Data Firehose Delivery Stream HTTP Endpoint Delivery Specifications](https://docs.aws.amazon.com/firehose/latest/dev/httpdeliveryrequestresponse.html))
