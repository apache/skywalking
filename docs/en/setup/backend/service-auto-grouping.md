# Service Auto Grouping
SkyWalking supports various default and customized dashboard templates. 
Each template provides the reasonable layout for the services in the particular field. 
Such as, services with a language agent installed 
could have different metrics with service detected by the service mesh observability solution, 
and different with SkyWalking's self-observability metrics dashboard.

Therefore, since 8.3.0, SkyWalking OAP would generate the group based on this simple naming format.

### ${service name} = [${group name}::]${logic name}

Once the service name includes double colons(`::`), the literal string before the colons would be considered as the group name.
In the latest GraphQL query, the group name has been provided as an option parameter.
> getAllServices(duration: Duration!, group: String): [Service!]!

RocketBot UI dashboards(`Standard` type) support the `group name` for default and custom configurations.