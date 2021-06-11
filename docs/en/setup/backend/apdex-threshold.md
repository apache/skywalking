# Apdex threshold

Apdex is a measure of response time based against a set threshold. It measures the ratio of satisfactory response times
to unsatisfactory response times. The response time is measured from an asset request to completed delivery back to 
the requestor.
 
A user defines a response time threshold T. All responses handled in T or less time satisfy the user.
 
For example, if T is 1.2 seconds and a response completes in 0.5 seconds, then the user is satisfied. All responses 
greater than 1.2 seconds dissatisfy the user. Responses greater than 4.8 seconds frustrate the user.

The apdex threshold T can be configured in `service-apdex-threshold.yml` file or via [Dynamic Configuration](dynamic-config.md). 
The `default` item will apply to a service that isn't defined in this configuration as the default threshold.

## Configuration Format

The configuration content includes the names and thresholds of the services:

```yml
# default threshold is 500ms
default: 500
# example:
# the threshold of service "tomcat" is 1s
# tomcat: 1000
# the threshold of service "springboot1" is 50ms
# springboot1: 50
```
