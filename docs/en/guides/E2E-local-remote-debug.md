# Using E2E local remote debugging
The E2E remote debugging port of service containers is `5005`. If the developer wants to use remote debugging, he needs to add remote debugging parameters to the start service command, and then expose the port `5005`. 

For example, this is the configuration of a container in the [skywalking/test/e2e/e2e-test/docker/base-compose.yml](https://github.com/apache/skywalking/blob/master/test/e2e/e2e-test/docker/base-compose.yml). [JAVA_OPTS](https://github.com/apache/skywalking/blob/190ca93b6bf48e9d966de5b05cd6490ba54b7266/docker/oap/docker-entrypoint.sh) is a preset variable for passing additional parameters in the AOP service startup command, so we only need to add the JAVA remote debugging parameters `agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` to the configuration and exposes the port `5005`.
```yml
oap:
    image: skywalking/oap:latest
    expose:
      ...
      - 5005
    ...
    environment:
      ...
      JAVA_OPTS: >-
        ...
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
    ...
```
At last, if the E2E test failed and is retrying, the developer can get the ports mapping in the file `skywalking/test/e2e/e2e-test/remote_real_port` and selects the host port of the corresponding service for remote debugging. For example,
```bash
#remote_real_port

#The remote debugging port on the host is 32783
oap-localhost:32783 

#The remote debugging port on the host is 32782
provider-localhost:32782 
```