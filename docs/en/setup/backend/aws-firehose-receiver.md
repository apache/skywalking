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

The following blogs demonstrate complete setup process for AWS S3 and API Gateway:

* [Monitoring DynamoDB with SkyWalking](https://skywalking.apache.org/blog/2023-03-13-skywalking-aws-dynamodb/) 
* [Monitoring AWS EKS and S3 with SkyWalking](https://skywalking.apache.org/blog/2023-03-12-skywalking-aws-s3-eks/)

## Supported metrics

| Description                             | Configuration File                  | Data Source                                                                                                                                       |
|-----------------------------------------|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| Metrics of AWS Cloud S3                 | otel-rules/aws-s3/s3-service.yaml   | AWS CloudWatcher Metrics Stream -> AWS Firehose delivery stream -> SkyWalking OAP Server with [AWS Firehose receiver](./aws-firehose-receiver.md) |
| Metrics of AWS DynamoDB | otel-rules/aws-dynamodb/dynamodb-service.yaml  | AWS CloudWatcher Metrics Stream -> AWS Firehose delivery stream -> SkyWalking OAP Server with [AWS Firehose receiver](./aws-firehose-receiver.md) |
| Metrics of AWS DynamoDB | otel-rules/aws-dynamodb/dynamodb-endpoint.yaml | AWS CloudWatcher Metrics Stream -> AWS Firehose delivery stream -> SkyWalking OAP Server with [AWS Firehose receiver](./aws-firehose-receiver.md) |
| Metrics of AWS API Gateway | otel-rules/aws-gateway/gateway-service.yaml | AWS CloudWatcher Metrics Stream -> AWS Firehose delivery stream -> SkyWalking OAP Server with [AWS Firehose receiver](./aws-firehose-receiver.md) |
| Metrics of AWS API Gateway | otel-rules/aws-gateway/gateway-endpoint.yaml | AWS CloudWatcher Metrics Stream -> AWS Firehose delivery stream -> SkyWalking OAP Server with [AWS Firehose receiver](./aws-firehose-receiver.md) |

## Notice

1. Only OpenTelemetry format is supported (refer to [Metric streams output formats](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-metric-streams-formats.html))
2. As AWS Firehose only support HTTPS scheme(through port `443`), it's required to enable TSL and `acceptProxyRequest`, or put a proxy(e.g. Nginx, Envoy) in front of OAP's Firehose receiver (refer to [Amazon Kinesis Data Firehose Delivery Stream HTTP Endpoint Delivery Specifications](https://docs.aws.amazon.com/firehose/latest/dev/httpdeliveryrequestresponse.html).
3. AWS Firehose receiver support setting accessKey for Kinesis Data Firehose, please refer to [configuration vocabulary](./configuration-vocabulary.md)
