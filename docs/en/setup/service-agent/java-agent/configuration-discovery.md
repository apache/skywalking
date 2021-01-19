# CDS - Configuration Discovery Service

CDS - Configuration Discovery Service provides the dynamic configuration for the agent, defined in [gRPC](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/ConfigurationDiscoveryService.proto).

## Configuration Format

The configuration content includes the service name and their configs. The 
```yml
configurations:
  //service name
  serviceA:
    // Configurations of service A
    // Key and Value are determined by the agent side.
    // Check the agent setup doc for all available configurations.
    key1: value1
    key2: value2
    ...
  serviceB:
    ...
```

## Available key(s) and value(s) in Java Agent.
Java agent supports the following dynamic configurations.

|        Config Key         |                      Value Description                       | Value Format Example  | Required Plugin(s) |
| :-----------------------: | :----------------------------------------------------------: | :-------------------: | :----------------: |
| agent.sample_n_per_3_secs |          The number of sampled traces per 3 seconds          |          -1           | - |

* `Required plugin(s)`, the configuration affects only when the required plugins activated.
