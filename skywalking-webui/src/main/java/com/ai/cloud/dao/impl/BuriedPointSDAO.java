/**
 * 
 */
package com.ai.cloud.dao.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import com.ai.cloud.dao.inter.IBuriedPointSDAO;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.HBaseConnectionUtil;
import com.ai.cloud.util.common.SortUtil;
import com.ai.cloud.vo.mvo.BuriedPointEntry;
import com.sun.tools.internal.ws.wsdl.framework.Entity;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午3:21:26
 * @version V0.1
 */
@Repository
public class BuriedPointSDAO implements IBuriedPointSDAO {

	private static Logger logger = LogManager.getLogger(BuriedPointSDAO.class);

	/***
	 * 查询指定traceId的调用链日志
	 * 
	 * @param tableName
	 * @param traceId
	 * @return
	 * @throws IOException
	 */
	public Map<String, BuriedPointEntry> queryLogByTraceId(String tableName, String traceId) throws IOException {
		Table table = HBaseConnectionUtil.getConnection().getTable(TableName.valueOf(tableName));
		Get g = new Get(Bytes.toBytes(traceId));
		Result r = table.get(g);
		Map<String, BuriedPointEntry> traceLogMap = new HashMap<String, BuriedPointEntry>();
		Map<String, BuriedPointEntry> rpcMap = new HashMap<String, BuriedPointEntry>();
		for (Cell cell : r.rawCells()) {
			if (cell.getValueArray().length > 0) {
				String colId = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),
						cell.getQualifierLength());
				BuriedPointEntry tmpEntry = BuriedPointEntry.convert(
						Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()), colId);
				System.out.println("=========" + tmpEntry);
				// 特殊处理RPC的服务端信息
				if (colId.endsWith(Constants.RPC_END_FLAG)) {
					rpcMap.put(colId.substring(0, colId.lastIndexOf(Constants.RPC_END_FLAG)), tmpEntry);
				} else {
					SortUtil.addCurNodeTreeMapKey(traceLogMap, colId, tmpEntry);
				}
			}
		}
		computeRPCInfo(rpcMap, traceLogMap);
		//合并处理RPC日志信息
		return traceLogMap;
	}
	
	/***
	 * 合并处理RPC日志信息
	 * @param rpcMap
	 * @param traceLogMap
	 */
	private void computeRPCInfo(Map<String, BuriedPointEntry> rpcMap, Map<String, BuriedPointEntry> traceLogMap) {
		// 合并处理
		if (rpcMap.size() > 0) {
			for (Entry<String, BuriedPointEntry> rpcVO : rpcMap.entrySet()) {
				String colId = rpcVO.getKey();
				if(traceLogMap.containsKey(colId)){
					BuriedPointEntry logVO = traceLogMap.get(colId);
					logVO.addTimeLine(rpcVO.getValue().getStartDate(), rpcVO.getValue().getCost());
				}
			}
		}
	}

}
