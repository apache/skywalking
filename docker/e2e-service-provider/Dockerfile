FROM apache/skywalking-java-agent:8.9.0-java8

COPY ./e2e-service-provider-2.0.0.jar /services_provider.jar

CMD ["java", "-jar", "/services_provider.jar"]