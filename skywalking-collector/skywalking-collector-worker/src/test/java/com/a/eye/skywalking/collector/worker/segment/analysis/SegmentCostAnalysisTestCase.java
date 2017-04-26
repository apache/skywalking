package com.a.eye.skywalking.collector.worker.segment.analysis;

import com.a.eye.skywalking.collector.actor.LocalSyncWorkerRef;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRef;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentCostSave;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class SegmentCostAnalysisTestCase {

    @Test
    public void aggWorkRefs() throws WorkerNotFoundException, NoSuchFieldException, IllegalAccessException {
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();

        WorkerRef workerRef = new LocalSyncWorkerRef(SegmentCostSave.Role.INSTANCE, null);
        localWorkerContext.put(workerRef);

        SegmentCostAnalysis analysis = new SegmentCostAnalysis(SegmentCostAnalysis.Role.INSTANCE, null, localWorkerContext);
        WorkerRefs workerRefs = analysis.aggWorkRefs();

        Field testAField = workerRefs.getClass().getDeclaredField("workerRefs");
        testAField.setAccessible(true);
        List<LocalSyncWorkerRef> list = (List<LocalSyncWorkerRef>)testAField.get(workerRefs);

        Assert.assertEquals(workerRef, list.get(0));
    }
}
