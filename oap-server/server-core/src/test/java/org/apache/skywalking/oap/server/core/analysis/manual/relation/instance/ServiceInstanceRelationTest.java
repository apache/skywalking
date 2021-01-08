package org.apache.skywalking.oap.server.core.analysis.manual.relation.instance;

import org.junit.Assert;
import org.junit.Test;

public class ServiceInstanceRelationTest {
    @Test
    public void testServiceInstanceRelationClientSideMetricsEquals() {
        ServiceInstanceRelationClientSideMetrics thisObject = new ServiceInstanceRelationClientSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationClientSideMetrics otherObject = new ServiceInstanceRelationClientSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationClientSideMetricsNotEquals() {
        ServiceInstanceRelationClientSideMetrics thisObject = new ServiceInstanceRelationClientSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationClientSideMetrics otherObject = new ServiceInstanceRelationClientSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationServerSideMetricsEquals() {
        ServiceInstanceRelationServerSideMetrics thisObject = new ServiceInstanceRelationServerSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationServerSideMetrics otherObject = new ServiceInstanceRelationServerSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationServerSideMetricsNotEquals() {
        ServiceInstanceRelationServerSideMetrics thisObject = new ServiceInstanceRelationServerSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationServerSideMetrics otherObject = new ServiceInstanceRelationServerSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
