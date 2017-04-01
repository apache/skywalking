package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(MergeAnalysisMember.class)
public class MergeAnalysisMemberTestCase {

    private MergeAnalysisMember member;
    private MergePersistenceData persistenceData;

    @Before
    public void init() throws Exception {
        member = mock(MergeAnalysisMember.class);
        persistenceData = mock(MergePersistenceData.class);
        MergeData mergeData = mock(MergeData.class);

        when(member, "getPersistenceData").thenReturn(persistenceData);
        when(persistenceData.getElseCreate(Mockito.anyString())).thenReturn(mergeData);

        doCallRealMethod().when(member).setMergeData(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSetMergeDataNotFull() throws Exception {
        when(persistenceData.size()).thenReturn(WorkerConfig.Persistence.Data.size - 1);

        member.setMergeData("segment_1", "column", "value");
        Mockito.verify(member, Mockito.never()).aggregation();
    }

    @Test
    public void testSetMergeDataFull() throws Exception {
        when(persistenceData.size()).thenReturn(WorkerConfig.Persistence.Data.size);

        member.setMergeData("segment_1", "column", "value");
        Mockito.verify(member, Mockito.times(1)).aggregation();
    }

    @Test
    public void testPushOne() throws Exception {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("segment_1").setMergeData("column", "value");

        when(member, "getPersistenceData").thenReturn(persistenceData);
        doCallRealMethod().when(member).pushOne();

        Assert.assertEquals("segment_1", member.pushOne().getId());
    }
}
