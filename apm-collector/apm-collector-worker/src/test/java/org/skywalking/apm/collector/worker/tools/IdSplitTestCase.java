package org.skywalking.apm.collector.worker.tools;

import org.junit.Test;
import org.skywalking.apm.collector.worker.Const;

/**
 * @author pengys5
 */
public class IdSplitTestCase {

    @Test
    public void testIdSplit() {
        String id = "201703221502..-..portal-service..-..[127.0.0.1:8002]";
        String[] ids = id.split(Const.IDS_SPLIT);
        for (String splitId : ids) {
//            System.out.println(splitId);
        }
    }
}
