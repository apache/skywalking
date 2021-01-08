package org.apache.skywalking.oap.server.core.analysis.manual.relation.service;

import org.junit.Assert;
import org.junit.Test;

public class ServiceRelationTest {
    @Test
    public void testServiceRelationClientSideMetricsEquals() {
        ServiceRelationClientSideMetrics thisObject = new ServiceRelationClientSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationClientSideMetrics otherObject = new ServiceRelationClientSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationClientSideMetricsNotEquals() {
        ServiceRelationClientSideMetrics thisObject = new ServiceRelationClientSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationClientSideMetrics otherObject = new ServiceRelationClientSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationServerSideMetricsEquals() {
        ServiceRelationServerSideMetrics thisObject = new ServiceRelationServerSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationServerSideMetrics otherObject = new ServiceRelationServerSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationServerSideMetricsNotEquals() {
        ServiceRelationServerSideMetrics thisObject = new ServiceRelationServerSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationServerSideMetrics otherObject = new ServiceRelationServerSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
