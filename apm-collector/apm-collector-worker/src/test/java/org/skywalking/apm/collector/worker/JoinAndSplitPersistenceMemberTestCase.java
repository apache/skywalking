package org.skywalking.apm.collector.worker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.worker.mock.MockEsBulkClient;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitData;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitPersistenceData;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {TestJoinAndSplitPersistenceMember.class, EsClient.class})
@PowerMockIgnore( {"javax.management.*"})
public class JoinAndSplitPersistenceMemberTestCase {

    private TestJoinAndSplitPersistenceMember mergePersistenceMember;
    private JoinAndSplitPersistenceData persistenceData;

    @Before
    public void init() throws Exception {
        MockEsBulkClient mockEsBulkClient = new MockEsBulkClient();
        mockEsBulkClient.createMock();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        mergePersistenceMember = PowerMockito.spy(new TestJoinAndSplitPersistenceMember(TestJoinAndSplitPersistenceMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        persistenceData = mock(JoinAndSplitPersistenceData.class);
        JoinAndSplitData joinAndSplitData = mock(JoinAndSplitData.class);

        when(mergePersistenceMember, "getPersistenceData").thenReturn(persistenceData);
        when(persistenceData.getOrCreate(Mockito.anyString())).thenReturn(joinAndSplitData);

        doCallRealMethod().when(mergePersistenceMember).analyse(Mockito.any(JoinAndSplitData.class));
    }

    @Test
    public void testAnalyse() throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        JoinAndSplitData joinAndSplitData = new JoinAndSplitData(id);
        joinAndSplitData.set("Column", "VALUE");

        mergePersistenceMember.analyse(joinAndSplitData);
    }
}
