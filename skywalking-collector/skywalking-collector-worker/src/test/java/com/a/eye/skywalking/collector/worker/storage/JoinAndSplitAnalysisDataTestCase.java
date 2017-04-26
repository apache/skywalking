package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class JoinAndSplitAnalysisDataTestCase {

    @Test
    public void getOrCreate() {
        JoinAndSplitAnalysisData joinAndSplitAnalysisData = new JoinAndSplitAnalysisData();
        JoinAndSplitData joinAndSplitData = joinAndSplitAnalysisData.getOrCreate("Test1");

        JoinAndSplitData joinAndSplitData_1 = joinAndSplitAnalysisData.getOrCreate("Test1");
        Assert.assertEquals(joinAndSplitData, joinAndSplitData_1);
    }

    @Test
    public void asMap() {
        JoinAndSplitAnalysisData joinAndSplitAnalysisData = new JoinAndSplitAnalysisData();
        JoinAndSplitData joinAndSplitData = joinAndSplitAnalysisData.getOrCreate("Test1");

        JoinAndSplitData joinAndSplitData_1 = joinAndSplitAnalysisData.asMap().get("Test1");
        Assert.assertEquals(joinAndSplitData, joinAndSplitData_1);
    }
}
