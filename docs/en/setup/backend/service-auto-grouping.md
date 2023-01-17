# Service Auto Grouping
SkyWalking supports various default and customized dashboard templates. 
Each template provides an appropriate layout for services in a particular field. 
For example, the metrics for services with language agents installed 
may be different from that of services detected by the service mesh observability solution as well as SkyWalking's self-observability metrics dashboard.

Therefore, since version 8.3.0, the SkyWalking OAP has generated the groups based on this simple naming format:

### ${service name} = [${group name}::]${logic name}

If the service name includes double colons (`::`), the literal string before the colons is taken as the group name.
In the latest GraphQL query, the group name has been provided as an optional parameter.
> getAllServices(duration: Duration!, group: String): [Service!]!

RocketBot UI dashboards (`Standard` type) support the `group name` for default and custom configurations.
