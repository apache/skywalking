# Use the Bitnami Kafka image as the base image
FROM bitnami/kafka:latest

# Download the JMX Prometheus Java Agent
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.18.0/jmx_prometheus_javaagent-0.18.0.jar /etc/jmx_prometheus_javaagent-0.18.0.jar

# Set the necessary permissions for the jar file
USER root
RUN chmod 644 /etc/jmx_prometheus_javaagent-0.18.0.jar
