# Docker

This section introduces how to build your Java application image on top of this image.

```dockerfile
FROM apache/skywalking-java-agent:8.5.0-jdk8

# ... build your java application
```

You can start your Java application with `CMD` or `ENTRYPOINT`, but you don't need to care about the Java options to
enable SkyWalking agent, it should be adopted automatically.

# Kubernetes

This section introduces how to use this image as sidecar of Kubernetes service.

In Kubernetes scenarios, you can also use this agent image as a sidecar.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: agent-as-sidecar
spec:
  restartPolicy: Never

  volumes:
    - name: skywalking-agent
      emptyDir: { }

  containers:
    - name: agent-container
      image: apache/skywalking-java-agent:8.4.0-alpine
      volumeMounts:
        - name: skywalking-agent
          mountPath: /agent
      command: [ "/bin/sh" ]
      args: [ "-c", "cp -R /skywalking/agent /agent/" ]

    - name: app-container
      image: springio/gs-spring-boot-docker
      volumeMounts:
        - name: skywalking-agent
          mountPath: /skywalking
      env:
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/skywalking/agent/skywalking-agent.jar"
```

