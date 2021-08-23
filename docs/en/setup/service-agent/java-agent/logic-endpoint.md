# The Logic Endpoint
In default, all the RPC server-side names as entry spans, such as RESTFul API path and gRPC service name, would be endpoints with metrics.
At the same time, SkyWalking introduces the logic endpoint concept, which allows plugins and users to add new endpoints without adding new spans.
The following logic endpoints are added automatically by plugins.
1. GraphQL Query and Mutation are logic endpoints by using the names of them.
1. Spring's ScheduledMethodRunnable jobs are logic endpoints. The name format is `SpringScheduled`/`${className}`/`${methodName}`.
1. Apache ShardingSphere ElasticJob's jobs are logic endpoints. The name format is `ElasticJob`/`${jobName}`.
1. XXLJob's jobs are logic endpoints. The name formats include `xxl-job`/`MethodJob`/`${className}`.`${methodName}`, `xxl-job`/`ScriptJob`/`${GlueType}`/`id`/`${jobId}`, and `xxl-job`/`SimpleJob`/`${className}`.
1. Quartz(optional plugin)'s jobs are logic endpoints. the name format is `quartz-scheduler`/`${className}`.

User could use the SkyWalking's application toolkits to add the tag into the local span to label the span as a logic endpoint in the analysis stage.
The tag is, key=`x-le` and value = `{"logic-span":true}`.