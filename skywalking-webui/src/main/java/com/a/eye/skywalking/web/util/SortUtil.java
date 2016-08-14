/**
 *
 */
package com.a.eye.skywalking.web.util;

import com.a.eye.skywalking.web.dto.TraceNodeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class SortUtil {

    private static Logger logger = LogManager.getLogger(SortUtil.class);

    public static void addCurNodeTreeMapKey(Map<String, TraceNodeInfo> reMap, String colId,
                                            TraceNodeInfo tmpEntry) {
        reMap.put(colId, tmpEntry);
        // 根据当前Id查找上级，如果不存在，插入空，再看上级，如果不存在还插入空，直到根"0"
        while (colId.indexOf(Constants.VAL_SPLIT_CHAR) > -1) {
            colId = colId.substring(0, colId.lastIndexOf(Constants.VAL_SPLIT_CHAR));
            if (!addParentNodeTreeMapKey(reMap, colId)) {
                break;
            }
        }
    }

    private static boolean addParentNodeTreeMapKey(Map<String, TraceNodeInfo> reMap, String colId) {
        if (reMap.containsKey(colId)) {
            return false;
        } else {
            // 增加虚拟节点
            reMap.put(colId, TraceNodeInfo.addLostBuriedPointEntry(colId));
            // 根据当前Id查找上级，如果不存在，插入空，再看上级，如果不存在还插入空，直到根"0"
            while (colId.indexOf(Constants.VAL_SPLIT_CHAR) > -1) {
                colId = colId.substring(0, colId.lastIndexOf(Constants.VAL_SPLIT_CHAR));
                if (!addParentNodeTreeMapKey(reMap, colId)) {
                    break;
                }
            }
            return false;
        }
    }
}
