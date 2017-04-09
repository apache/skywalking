package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(TestMergeAnalysisMember.class)
@PowerMockIgnore({"javax.management.*"})
public class MergeAnalysisMemberTestCase {

    private TestMergeAnalysisMember mergeAnalysisMember;
    private MergePersistenceData persistenceData;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        mergeAnalysisMember = PowerMockito.spy(new TestMergeAnalysisMember(TestMergeAnalysisMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        persistenceData = mock(MergePersistenceData.class);
        MergeData mergeData = mock(MergeData.class);

        when(mergeAnalysisMember, "getPersistenceData").thenReturn(persistenceData);
        when(persistenceData.getElseCreate(Mockito.anyString())).thenReturn(mergeData);

        doCallRealMethod().when(mergeAnalysisMember).setMergeData(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSetMergeDataNotFull() throws Exception {
        when(persistenceData.size()).thenReturn(CacheSizeConfig.Cache.Analysis.size - 1);

        mergeAnalysisMember.setMergeData("segment_1", "column", "value");
        Mockito.verify(mergeAnalysisMember, Mockito.never()).aggregation();
    }

    @Test
    public void testSetMergeDataFull() throws Exception {
        when(persistenceData.size()).thenReturn(CacheSizeConfig.Cache.Analysis.size);

        mergeAnalysisMember.setMergeData("segment_1", "column", "value");
        Mockito.verify(mergeAnalysisMember, Mockito.times(1)).aggregation();
    }

    @Test
    public void testPushOne() throws Exception {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("segment_1").setMergeData("column", "value");

        when(mergeAnalysisMember, "getPersistenceData").thenReturn(persistenceData);
        doCallRealMethod().when(mergeAnalysisMember).pushOne();

        Assert.assertEquals("segment_1", mergeAnalysisMember.pushOne().getId());
        Assert.assertEquals(null, mergeAnalysisMember.pushOne());
    }
}
