/**
 * 
 */
package com.ai.cloud.util.common;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.ai.cloud.dao.impl.BuriedPointSDAO;
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

	/**
	 * 测试读取hbase 测试自动补充父级节点 测试排序
	 * 
	 * @throws IOException
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * 
	 */
	@Test
	public void testSelectByTraceId() throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		BuriedPointSDAO sdao = new BuriedPointSDAO();
		Map<String, TraceLogEntry> bpe = sdao.queryLogByTraceId(Constants.TABLE_NAME_CHAIN,
				"71e28364128847b3a12626966b60fd8f123");

		List<TraceLogEntry> keyList = new ArrayList<TraceLogEntry>();

		keyList.addAll(bpe.values());

		Collections.sort(keyList, new Comparator<TraceLogEntry>() {
			@Override
			public int compare(TraceLogEntry arg0, TraceLogEntry arg1) {
				return arg0.getColId().compareTo(arg1.getColId());
			}
		});

		int m = 1;
		for (TraceLogEntry tmpEntry : keyList) {
			logger.info("sort result level:{} : {}", m++, tmpEntry);
		}

	}

	/***
	 * 深度补全测试
	 * 
	 * @throws IOException
	 */
	@Test
	public void testGenTreeMapKey() throws IOException {

		StringBuffer sb = new StringBuffer("0");

		/***
		 * 深度测试 level 5 [ 11ms ] level 10[ 11ms ] level 15[ 11ms ] level 20[
		 * 12ms ] level 50[ 12ms ] level 100[ 12ms ] level 500[ 12ms ] level
		 * 1000[ 16ms ] level 2000[ 28ms ] level 5000[ 144ms ] level10000[ 445ms
		 * ]
		 */
		for (int i = 1; i < 10000; i++) {
			sb.append(".0");
		}

		String colId = sb.toString();
		TraceLogEntry tmpEntry = null;
		Map<String, TraceLogEntry> reMap = new HashMap<String, TraceLogEntry>();
		long startTime = System.currentTimeMillis();
		logger.info("start time : {}", startTime);

		addCurNodeTreeMapKey(reMap, colId, tmpEntry);

		long endTime = System.currentTimeMillis();
		logger.info("end time : {}", endTime);
		logger.info("run time : {} ms", (endTime - startTime));

		// List<String> keyList = new ArrayList<String>();
		// keyList.addAll(reMap.keySet());
		// Collections.sort(keyList);
		// int m = 1;
		// for (String str : keyList) {
		// logger.info("sort result level:{} : {}", m++, str);
		// }

	}

	/***
	 * collection sort 性能测试
	 * 
	 * @param args
	 */
	@Test
	public void testSortList() {
		List<String> StrList = new ArrayList<String>();
		Random random = new Random(System.currentTimeMillis());
		StrList.add("0");

		/***
		 * Collections.sort(StrList)性能 1000条/19 ms 10000条/42 ms 100000条/129 ms
		 * 1000000条/1070 ms
		 */
		for (int m = 0; m < 1000; m++) {
			String radNum = "" + Math.abs(random.nextLong());
			StringBuffer tmpBf = new StringBuffer("0");
			for (int n = 0; n < radNum.length(); n++) {
				tmpBf.append(".").append(radNum.charAt(n));
			}
			StrList.add(tmpBf.toString());
		}

		long startTime = System.currentTimeMillis();
		logger.info("start time : {}", startTime);

		Collections.sort(StrList);

		long endTime = System.currentTimeMillis();
		logger.info("end time : {}", endTime);
		logger.info("run time : {} ms", (endTime - startTime));

		// int i = 1;
		// for (String tmpStr : StrList) {
		// logger.info("sort {} : {}", i++, tmpStr);
		// }
	}
}
