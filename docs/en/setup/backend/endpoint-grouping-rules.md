# Group Parameterized Endpoints
In most cases, the endpoint should be detected automatically through the language agents, service mesh observability solution, 
or configuration of meter system.

There are some special cases, especially when people use REST style URI, the application codes put the parameter in the endpoint name, 
such as putting order id in the URI, like `/prod/ORDER123` and `/prod/ORDER123`. But logically, people expect they could
have an endpoint name like `prod/{order-id}`. This is the feature of parameterized endpoint grouping designed for.

Current, user could set up grouping rules through the static YAML file, named `endpoint-name-grouping.yml`,
or use [Dynamic Configuration](dynamic-config.md) to initial and update the endpoint grouping rule.

## Configuration Format
No matter in static local file or dynamic configuration value, they are sharing the same YAML format.

```yaml
grouping:
  # Endpoint of the service would follow the following rules
  - service-name: serviceA
    rules:
      # Logic name when the regex expression matched.
      - endpoint-name: /prod/{id}
        regex: \/prod\/.+
```