package org.skywalking.apm.collector.actor.selector;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.actor.WorkerRef;

import java.util.ArrayList;
import java.util.List;

import static org.powermock.api.mockito.PowerMockito.mock;

/**
 * @author pengys5
 */
public class RollingSelectorTestCase {

    @Test
    public void testSelect() {
        List<WorkerRef> members = new ArrayList<>();
        WorkerRef workerRef_1 = mock(WorkerRef.class);
        WorkerRef workerRef_2 = mock(WorkerRef.class);
        WorkerRef workerRef_3 = mock(WorkerRef.class);

        members.add(workerRef_1);
        members.add(workerRef_2);
        members.add(workerRef_3);

        Object message = new Object();

        RollingSelector selector = new RollingSelector();

        WorkerRef selected_1 = selector.select(members, message);
        Assert.assertEquals(workerRef_2.hashCode(), selected_1.hashCode());

        WorkerRef selected_2 = selector.select(members, message);
        Assert.assertEquals(workerRef_3.hashCode(), selected_2.hashCode());

        WorkerRef selected_3 = selector.select(members, message);
        Assert.assertEquals(workerRef_1.hashCode(), selected_3.hashCode());
    }
}
