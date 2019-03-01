package org.apache.skywalking.oap.server.core.analysis.indicator;

import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author liuhaoyang
 **/
public class MaxIndicatorTest {

    @Test
    public void testEntranceCombine() {
        MaxIndicatorImpl impl = new MaxIndicatorImpl();
        impl.combine(10);
        impl.combine(5);
        impl.combine(20);
        impl.calculate();
        Assert.assertEquals(20, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        MaxIndicatorImpl impl = new MaxIndicatorImpl();
        impl.combine(10);
        impl.combine(5);

        MaxIndicatorImpl impl2 = new MaxIndicatorImpl();
        impl.combine(2);
        impl.combine(6);

        impl.combine(impl2);
        Assert.assertEquals(10, impl.getValue());
    }

    public class MaxIndicatorImpl extends MaxIndicator{
        @Override public String id() {
            return null;
        }

        @Override public Indicator toHour() {
            return null;
        }

        @Override public Indicator toDay() {
            return null;
        }

        @Override public Indicator toMonth() {
            return null;
        }

        @Override public int remoteHashCode() {
            return 0;
        }

        @Override public void deserialize(RemoteData remoteData) {

        }

        @Override public RemoteData.Builder serialize() {
            return null;
        }
    }
}
