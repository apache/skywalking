package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;
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
@PrepareForTest({TestMergePersistenceMember.class, EsClient.class})
@PowerMockIgnore({"javax.management.*"})
public class MergePersistenceMemberTestCase {

    private TestMergePersistenceMember mergePersistenceMember;
    private MergePersistenceData persistenceData;

    @Before
    public void init() throws Exception {
        MockEsBulkClient mockEsBulkClient = new MockEsBulkClient();
        mockEsBulkClient.createMock();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        mergePersistenceMember = PowerMockito.spy(new TestMergePersistenceMember(TestMergePersistenceMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        persistenceData = mock(MergePersistenceData.class);
        MergeData mergeData = mock(MergeData.class);

        when(mergePersistenceMember, "getPersistenceData").thenReturn(persistenceData);
        when(persistenceData.getElseCreate(Mockito.anyString())).thenReturn(mergeData);

        doCallRealMethod().when(mergePersistenceMember).analyse(Mockito.any(MergeData.class));
    }

    @Test
    public void testAnalyse() throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        MergeData mergeData = new MergeData(id);
        mergeData.setMergeData("Column", "Value");

//        mergePersistenceMember.analyse(mergeData);
//        mergePersistenceMember.onWork(new EndOfBatchCommand());
    }
}
