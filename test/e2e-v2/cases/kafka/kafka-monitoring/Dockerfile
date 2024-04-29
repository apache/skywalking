# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Use the Bitnami Kafka image as the base image
FROM bitnami/kafka:2.4.1

# Download the JMX Prometheus Java Agent and config file
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.18.0/jmx_prometheus_javaagent-0.18.0.jar /etc/jmx_prometheus_javaagent-0.18.0.jar
ADD https://raw.githubusercontent.com/prometheus/jmx_exporter/main/example_configs/kafka-2_0_0.yml /etc/kafka-2_0_0.yml

# Set the necessary permissions for the jar and yml
USER root
RUN chmod 644 /etc/jmx_prometheus_javaagent-0.18.0.jar
RUN chmod 644 /etc/kafka-2_0_0.yml
