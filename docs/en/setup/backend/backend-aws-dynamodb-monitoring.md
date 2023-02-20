# AWS DynamoDb monitoring
SkyWalking leverages Amazon Kinesis Data Filehose with [Amazon CloudWatch](https://aws.amazon.com/cn/cloudwatch/) to transfer the metrics into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. Amazon CloudWatch fetches metrics from DynamoDB and pushes metrics to SkyWalking OAP Server via Amazon Kinesis data firehose.
2. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Enable [AWS CloudWatch](https://aws.amazon.com/cn/cloudwatch/)
2. Create [Amazon Kinesis Data Filehose](https://aws.amazon.com/cn/kinesis/data-firehose/), set source to `Direct PUT`, set destination to  `HTTP Endpoint`, and set `HTTP EndPoint url` to `aws-firehose-receiver`'s port (refer to [aws-firehose-receiver](aws-firehose-receiver.md))

   Note that AWS requires that the `HTTP Endpoint URL` must be through HTTPS listening at 443, therefore need to load the certificate in [aws-firehose-receiver](aws-firehose-receiver.md) and set the port to 443.
   Or, you can use another gateway to accept the requests and route them to `aws-filehose-receiver`.
3. Create a [metric stream](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Metric-Streams.html), set namespace to DynanoDB, and set `Kinesis Data Firehose` to the firehose you just created.
4. Config [aws-firehose-receiver](aws-firehose-receiver.md) to receive data.

### DynamoDB Monitoring
DynamoDB monitoring provides monitoring of the status and resources of the DynamoDB server. AWS user id is cataloged as a `Layer: AWS_DYNAMODB` `Service` in OAP.
Each DynamoDB table is cataloged as an `Endpoint` in OAP.

#### Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|-----|-----|-----|--|
| Read Usage |   unit/s  | consumed_read_capacity_units <br /> provisioned_read_capacity_units| The situation of read capacity units consumed and provisioned over the specified time period | Amazon CloudWatch |
| Write Usage |   unit/s  | consumed_write_capacity_units <br /> provisioned_write_capacity_units| The situation of write capacity units consumed and provisioned over the specified time period | Amazon CloudWatch |
| Successful Request Latency |  ms   | get_successful_request_latency <br /> put_successful_request_latency <br /> query_successful_request_latency <br /> scan_successful_request_latency | The latency of successful request | Amazon CloudWatch |
| TTL Deleted Item count |     | time_to_live_deleted_item_count | The count of items deleted by TTL | Amazon CloudWatch |
| Throttle Events|     | read_throttle_events <br /> write_throttle_events | Requests to DynamoDB that exceed the provisioned read/write capacity units for a table or a global secondary index. | Amazon CloudWatch |
| Throttled Requests |     | read_throttled_requests <br /> write_throttled_requests | Requests to DynamoDB that exceed the provisioned throughput limits on a resource (such as a table or an index). | Amazon CloudWatch |
| Scan/Query Operation Returned Item Ccount |     | scan_returned_item_count <br/>query_returned_item_count<br /> | The number of items returned by Query, Scan or ExecuteStatement (select) operations during the specified time period. | Amazon CloudWatch |
| System Errors |    | read_system_errors<br />write_system_errors | The requests to DynamoDB or Amazon DynamoDB Streams that generate an HTTP 500 status code during the specified time period. | Amazon CloudWatch |
| User Errors |    | user_errors | Requests to DynamoDB or Amazon DynamoDB Streams that generate an HTTP 400 status code during the specified time period.| Amazon CloudWatch |
| Condition Checked Fail Requests |     | conditional_check_failed_requests  | The number of failed attempts to perform conditional writes.  | Amazon CloudWatch |
| Transaction Conflict |    | transaction_conflict | Rejected item-level requests due to transactional conflicts between concurrent requests on the same items.  | Amazon CloudWatch |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/aws-dynamodb`.
The DynamoDB dashboard panel configurations are found in `/config/ui-initialized-templates/aws_dynamodb`.