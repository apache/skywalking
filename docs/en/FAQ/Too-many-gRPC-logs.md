### Problem
Too many GRPC log in the console

### Reason
Skywalking uses the GRPC framework to send data, and the GRPC framework reads log configuration files for log output.

### Resolve 
Add filter to `org.apache.skywalking.apm.dependencies` package in log configuration file
