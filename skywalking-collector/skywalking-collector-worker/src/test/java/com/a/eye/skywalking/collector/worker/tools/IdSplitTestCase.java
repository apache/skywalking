package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.worker.Const;
import org.junit.Test;

/**
 * @author pengys5
 */
public class IdSplitTestCase {

    @Test
    public void testIdSplit() {
        String id = "201703221502..-..portal-service..-..[127.0.0.1:8002]";
        String[] ids = id.split(Const.IDS_SPLIT);
        for (String splitId : ids) {
            System.out.println(splitId);
        }
    }
}
