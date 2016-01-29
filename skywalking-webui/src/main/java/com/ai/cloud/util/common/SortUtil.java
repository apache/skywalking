/**
 * 
 */
package com.ai.cloud.util.common;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.util.Constants;
import com.ai.cloud.vo.mvo.TraceLogEntry;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午2:03:10
 * @version V0.1
 */
public class SortUtil {

	private static Logger logger = LogManager.getLogger(SortUtil.class);

	/***
	 * 增加调度链路节点信息
	 * 
	 * @param reMap
	 * @param colId
	 * @param tmpEntry
	 */
	public static void addCurNodeTreeMapKey(Map<String, TraceLogEntry> reMap, String colId,
			TraceLogEntry tmpEntry) {
		reMap.put(colId, tmpEntry);
		// 根据当前Id查找上级，如果不存在，插入空，再看上级，如果不存在还插入空，直到根"0"
		while (colId.indexOf(Constants.VAL_SPLIT_CHAR) > -1) {
			colId = colId.substring(0, colId.lastIndexOf(Constants.VAL_SPLIT_CHAR));
			if (!addParentNodeTreeMapKey(reMap, colId)) {
				break;
			}
		}
	}

	/***
	 * 补充父级调度链路各节点信息
	 * 
	 * @param reMap
	 * @param colId
	 * @return
	 */
	private static boolean addParentNodeTreeMapKey(Map<String, TraceLogEntry> reMap, String colId) {
		if (reMap.containsKey(colId)) {
			return false;
		} else {
			// 增加虚拟节点
			reMap.put(colId, TraceLogEntry.addLostBuriedPointEntry(colId));
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
