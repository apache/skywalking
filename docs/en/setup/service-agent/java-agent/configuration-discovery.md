# ConfigurationDiscovery

ConfigurationDiscovery used to dynamic configuration for the agent.

## Configuration Format

The configuration content includes the service name and their configs, This configuration item is registered in OAP  [`dynamic-config`](../../backend/dynamic-config.md)

```yml
rules:
	//service name
  dubbox-provider:
  	// The number of sampled traces per 3 seconds
    agent.sample_n_per_3_secs: 1
    // If the operation name of the first span is matching, this segment should be ignored
    trace.ignore_path: /api/seller/seller/*
  dubbox-consumer:
  	// The number of sampled traces per 3 seconds
    agent.sample_n_per_3_secs: 1
    // If the operation name of the first span is matching, this segment should be ignored
    trace.ignore_path: /api/seller/seller/*
    ...
```

The service configuration items are as follows

|        Config Key         |                      Value Description                       | Value Format Example  |
| :-----------------------: | :----------------------------------------------------------: | :-------------------: |
| agent.sample_n_per_3_secs |          The number of sampled traces per 3 seconds          |          -1           |
|     trace.ignore_path     | If the operation name of the first span is matching, this segment should be ignored | /eureka/\*\*,/consul/\*\* |

**Note** The above dynamic configuration items have not yet been implemented on the agent side. The relevant implementation will be submitted in the next two days. The agent uses dynamic configuration and needs to register a dynamic configuration listener on the agent side. Not all agent configurations support dynamic configuration.

