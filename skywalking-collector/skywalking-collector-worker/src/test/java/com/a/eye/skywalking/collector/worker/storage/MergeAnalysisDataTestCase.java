package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class MergeAnalysisDataTestCase {

    @Test
    public void getOrCreate() {
        MergeAnalysisData mergeAnalysisData = new MergeAnalysisData();
        MergeData mergeData = mergeAnalysisData.getOrCreate("Test1");

        MergeData mergeData_1 = mergeAnalysisData.getOrCreate("Test1");
        Assert.assertEquals(mergeData, mergeData_1);
    }

    @Test
    public void asMap() {
        MergeAnalysisData mergeAnalysisData = new MergeAnalysisData();
        MergeData mergeData = mergeAnalysisData.getOrCreate("Test1");

        MergeData mergeData_1 = mergeAnalysisData.asMap().get("Test1");
        Assert.assertEquals(mergeData, mergeData_1);
    }
}
