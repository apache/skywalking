/**
 * 
 */
package com.ai.cloud.dao.impl;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import com.ai.cloud.util.common.StringUtil;
import com.ai.cloud.vo.mvo.TraceLogEntry;

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
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 */
	public Map<String, TraceLogEntry> queryLogByTraceId(String tableName, String traceId) throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Table table = HBaseConnectionUtil.getConnection().getTable(TableName.valueOf(tableName));
		Get g = new Get(Bytes.toBytes(traceId));
		Result r = table.get(g);
		Map<String, TraceLogEntry> traceLogMap = new HashMap<String, TraceLogEntry>();
		Map<String, TraceLogEntry> rpcMap = new HashMap<String, TraceLogEntry>();
		for (Cell cell : r.rawCells()) {
			if (cell.getValueArray().length > 0) {
				String colId = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),
						cell.getQualifierLength());
				TraceLogEntry tmpEntry = TraceLogEntry.convert(
						Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()), colId);
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
	private void computeRPCInfo(Map<String, TraceLogEntry> rpcMap, Map<String, TraceLogEntry> traceLogMap) {
		// 合并处理
		if (rpcMap.size() > 0) {
			for (Entry<String, TraceLogEntry> rpcVO : rpcMap.entrySet()) {
				String colId = rpcVO.getKey();
				if(traceLogMap.containsKey(colId)){
					TraceLogEntry logVO = traceLogMap.get(colId);
					TraceLogEntry serverLog = rpcVO.getValue();
					//如果RPC client端为空，则用server端信息
					if(StringUtil.isBlank(logVO.getStatusCodeStr()) || Constants.STATUS_CODE_9.equals(logVO.getStatusCodeStr())){
						serverLog.setColId(colId);
						traceLogMap.put(colId, serverLog);
					}else{
						TraceLogEntry clientLog = traceLogMap.get(colId);
						//客户端RPC显示特殊处理
						clientLog.setApplicationIdStr(clientLog.getApplicationIdStr() + " --> " + serverLog.getApplicationIdStr());
						clientLog.setViewPointId(serverLog.getViewPointId());
						clientLog.setViewPointIdSub(serverLog.getViewPointIdSub());
						clientLog.setAddress(serverLog.getAddress());
						if (StringUtil.isBlank(clientLog.getExceptionStack())){
							clientLog.setExceptionStack(serverLog.getExceptionStack());
						}
					}
					logVO.addTimeLine(rpcVO.getValue().getStartDate(), rpcVO.getValue().getCost());
				}else{
					traceLogMap.put(colId, rpcVO.getValue());
				}
			}
		}
	}

}
