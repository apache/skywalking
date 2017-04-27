package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.WorkerRef;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
public class HashCodeSelectorTestCase {

    @Test
    public void testSelect() {
        List<WorkerRef> members = new ArrayList<>();
        WorkerRef workerRef_1 = mock(WorkerRef.class);
        WorkerRef workerRef_2 = mock(WorkerRef.class);
        WorkerRef workerRef_3 = mock(WorkerRef.class);

        members.add(workerRef_1);
        members.add(workerRef_2);
        members.add(workerRef_3);

        AbstractHashMessage message_1 = mock(AbstractHashMessage.class);
        when(message_1.getHashCode()).thenReturn(9);

        AbstractHashMessage message_2 = mock(AbstractHashMessage.class);
        when(message_2.getHashCode()).thenReturn(10);

        AbstractHashMessage message_3 = mock(AbstractHashMessage.class);
        when(message_3.getHashCode()).thenReturn(11);

        HashCodeSelector selector = new HashCodeSelector();

        WorkerRef select_1 = selector.select(members, message_1);
        Assert.assertEquals(workerRef_1.hashCode(), select_1.hashCode());

        WorkerRef select_2 = selector.select(members, message_2);
        Assert.assertEquals(workerRef_2.hashCode(), select_2.hashCode());

        WorkerRef select_3 = selector.select(members, message_3);
        Assert.assertEquals(workerRef_3.hashCode(), select_3.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSelectError() {
        HashCodeSelector selector = new HashCodeSelector();
        selector.select(null, new Object());
    }
}
