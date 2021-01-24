package org.apache.skywalking.e2e.zabbix;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.UIConfigurationManagementClient;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SIMPLE_MICROMETER_METERS;

@Slf4j
@SkyWalkingE2E
public class ZabbixE2E extends SkyWalkingTestAdapter {

    @DockerCompose({"docker/zabbix/docker-compose.yml"})
    private DockerComposeContainer<?> compose;

    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    private UIConfigurationManagementClient graphql;

    @BeforeAll
    public void setUp() throws Exception {
        graphql = new UIConfigurationManagementClient(swWebappHostPort.host(), swWebappHostPort.port());
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    void testMetrics() throws Exception {
        for (String meterName : SIMPLE_MICROMETER_METERS) {
            LOGGER.info("verifying zabbix meter:{}", meterName);
            final ReadMetrics metrics = graphql.readMetrics(
                new ReadMetricsQuery().stepByMinute().metricsName(meterName)
            );
            LOGGER.info("zabbix metrics: {}", metrics);

            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(metrics.getValues());
            LOGGER.info("{}: {}", meterName, metrics);
        }
    }
}
