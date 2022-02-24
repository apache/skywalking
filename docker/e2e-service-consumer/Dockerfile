FROM apache/skywalking-java-agent:8.9.0-java8

COPY ./e2e-service-consumer-2.0.0.jar /services_consumer.jar

CMD ["java", "-jar", "/services_consumer.jar"]