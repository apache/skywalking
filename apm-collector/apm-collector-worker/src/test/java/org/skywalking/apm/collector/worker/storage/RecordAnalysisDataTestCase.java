package org.skywalking.apm.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class RecordAnalysisDataTestCase {

    @Test
    public void getOrCreate() {
        RecordAnalysisData recordAnalysisData = new RecordAnalysisData();
        RecordData recordData = recordAnalysisData.getOrCreate("Test1");

        RecordData recordData_1 = recordAnalysisData.getOrCreate("Test1");
        Assert.assertEquals(recordData, recordData_1);
    }

    @Test
    public void asMap() {
        RecordAnalysisData recordAnalysisData = new RecordAnalysisData();
        RecordData recordData = recordAnalysisData.getOrCreate("Test1");
        recordData.merge(null);

        RecordData recordData_1 = recordAnalysisData.asMap().get("Test1");
        Assert.assertEquals(recordData, recordData_1);
    }
}
