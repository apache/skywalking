package org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint;

import org.junit.Assert;
import org.junit.Test;

public class EndpointCallRelation {
    @Test
    public void testServiceInstanceRelationClientSideMetricsEquals() {
        EndpointRelationServerSideMetrics thisObject = new EndpointRelationServerSideMetrics();
        thisObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        thisObject.setTimeBucket(202101071505L);

        EndpointRelationServerSideMetrics otherObject = new EndpointRelationServerSideMetrics();
        otherObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationClientSideMetricsNotEquals() {
        EndpointRelationServerSideMetrics thisObject = new EndpointRelationServerSideMetrics();
        thisObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        thisObject.setTimeBucket(202101071505L);

        EndpointRelationServerSideMetrics otherObject = new EndpointRelationServerSideMetrics();
        otherObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
