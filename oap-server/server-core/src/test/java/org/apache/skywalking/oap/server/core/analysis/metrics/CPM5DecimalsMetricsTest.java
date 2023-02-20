package org.apache.skywalking.oap.server.core.analysis.metrics;

import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CPM5DecimalsMetricsTest {

    @Test
    public void testEntranceCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();
        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        impl.calculate();

        Assertions.assertEquals(1800000, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();
        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        CPM5DecimalsMetricsTest.CountMetricsImpl impl2 = new CountMetricsImpl();
        impl2.combine(5);
        impl2.combine(6);
        impl2.combine(7);

        impl.combine(impl2);

        impl.calculate();

        Assertions.assertEquals(3600000, impl.getValue());
    }

    @Test
    public void testDownSampleCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();
        impl.setTimeBucket(202302201608L);
        impl.combine(1);

        impl.calculate();

        Assertions.assertEquals(100000, impl.getValue());

        impl.setTimeBucket(impl.toTimeBucketInHour());
        impl.calculate();

        impl.setTimeBucket(impl.toTimeBucketInDay());
        impl.calculate();

        Assertions.assertEquals(69, impl.getValue());
    }

    private static class CountMetricsImpl extends CPM5DecimalsMetrics {
        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        protected StorageID id0() {
            return null;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }
    }
}