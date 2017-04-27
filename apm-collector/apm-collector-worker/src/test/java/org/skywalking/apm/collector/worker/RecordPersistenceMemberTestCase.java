package org.skywalking.apm.collector.worker;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.worker.storage.RecordData;
import org.skywalking.apm.collector.worker.storage.RecordPersistenceData;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(RecordPersistenceMember.class)
public class RecordPersistenceMemberTestCase {

    @Test
    public void analyse() throws Exception {
        RecordPersistenceMember member = PowerMockito.mock(RecordPersistenceMember.class, CALLS_REAL_METHODS);
        Logger logger = mock(Logger.class);
        when(member.logger()).thenReturn(logger);

        RecordPersistenceData data = mock(RecordPersistenceData.class);
        RecordData recordData = new RecordData("test1");
        when(data.getOrCreate(anyString())).thenReturn(recordData);

        when(member.getPersistenceData()).thenReturn(data);

        RecordData recordData_1 = new RecordData("test2");
        member.analyse(recordData_1);

        verify(data).hold();
        verify(data).release();

        member.analyse(new Object());
    }
}
