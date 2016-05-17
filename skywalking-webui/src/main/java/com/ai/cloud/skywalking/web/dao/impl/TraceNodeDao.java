package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dto.TraceNodeInfo;
import com.ai.cloud.skywalking.web.dto.TraceNodesResult;
import com.ai.cloud.skywalking.web.dao.inter.ITraceNodeDao;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.util.HBaseUtils;
import com.ai.cloud.skywalking.web.util.SortUtil;
import com.ai.cloud.skywalking.web.util.StringUtil;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.ColumnCountGetFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 16-3-30.
 */
@Repository
public class TraceNodeDao implements ITraceNodeDao {

    private String CALL_CHAIN_TABLE_NAME = "sw-call-chain";

    @Autowired
    private HBaseUtils hBaseUtils;


    @Override
    public TraceNodesResult queryTraceNodesByTraceId(String traceId) throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf(CALL_CHAIN_TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        g.setFilter(new ColumnCountGetFilter(Constants.MAX_SEARCH_SPAN_SIZE + 1));
        Result r = table.get(g);
        Map<String, TraceNodeInfo> traceLogMap = new HashMap<String, TraceNodeInfo>();
        Map<String, TraceNodeInfo> rpcMap = new HashMap<String, TraceNodeInfo>();
        TraceNodesResult result = new TraceNodesResult();
        if (r.rawCells().length < Constants.MAX_SEARCH_SPAN_SIZE) {
            for (Cell cell : r.rawCells()) {
                doDealSingleSpan(traceLogMap, rpcMap, cell);
            }
            computeRPCInfo(rpcMap, traceLogMap);
            result.setOverMaxQueryNodeNumber(false);
            result.setResult(traceLogMap.values());
        }else{
            result.setOverMaxQueryNodeNumber(true);
        }
        return result;
    }

    @Override
    public Collection<TraceNodeInfo> queryEntranceNodeByTraceId(String traceId) throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf(CALL_CHAIN_TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        g.addColumn("call-chain".getBytes(), "0".getBytes());
        g.addColumn("call-chain".getBytes(), "0-S".getBytes());
        g.addColumn("call-chain".getBytes(), "0.0".getBytes());
        Result r = table.get(g);

        Map<String, TraceNodeInfo> traceLogMap = new HashMap<String, TraceNodeInfo>();
        Map<String, TraceNodeInfo> rpcMap = new HashMap<String, TraceNodeInfo>();
        Cell cell = r.getColumnLatestCell("call-chain".getBytes(), "0".getBytes());
        if (cell == null){
            cell = r.getColumnLatestCell("call-chain".getBytes(), "0-S".getBytes());
        }
        doDealSingleSpan(traceLogMap, rpcMap, cell);

        cell = r.getColumnLatestCell("call-chain".getBytes(), "0.0".getBytes());
        doDealSingleSpan(traceLogMap, rpcMap, cell);

        computeRPCInfo(rpcMap, traceLogMap);
        return traceLogMap.values();
    }

    private void doDealSingleSpan(Map<String, TraceNodeInfo> traceLogMap, Map<String, TraceNodeInfo> rpcMap, Cell cell) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if (cell != null && cell.getValueArray().length > 0) {
            String colId = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),
                    cell.getQualifierLength());
            TraceNodeInfo tmpEntry = TraceNodeInfo.convert(
                    Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()), colId);
            // 特殊处理RPC的服务端信息
            if (colId.endsWith(Constants.RPC_END_FLAG)) {
                rpcMap.put(colId.substring(0, colId.lastIndexOf(Constants.RPC_END_FLAG)), tmpEntry);
            } else {
                SortUtil.addCurNodeTreeMapKey(traceLogMap, colId, tmpEntry);
            }
        }
    }

    private void computeRPCInfo(Map<String, TraceNodeInfo> rpcMap, Map<String, TraceNodeInfo> traceLogMap) {
        // 合并处理
        if (rpcMap.size() > 0) {
            for (Map.Entry<String, TraceNodeInfo> rpcVO : rpcMap.entrySet()) {
                String colId = rpcVO.getKey();
                if (traceLogMap.containsKey(colId)) {
                    TraceNodeInfo logVO = traceLogMap.get(colId);
                    TraceNodeInfo serverLog = rpcVO.getValue();
                    if (StringUtil.isBlank(logVO.getStatusCodeStr()) || Constants.STATUS_CODE_9.equals(logVO.getStatusCodeStr())) {
                        serverLog.setColId(colId);
                        traceLogMap.put(colId, serverLog);
                    } else {
                        TraceNodeInfo clientLog = traceLogMap.get(colId);
                        clientLog.setApplicationIdStr(clientLog.getApplicationIdStr() + " --> " + serverLog.getApplicationIdStr());
                        clientLog.setViewPointId(serverLog.getViewPointId());
                        clientLog.setViewPointIdSub(serverLog.getViewPointIdSub());
                        clientLog.setAddress(serverLog.getAddress());
                        if (StringUtil.isBlank(clientLog.getExceptionStack())) {
                            clientLog.setExceptionStack(serverLog.getExceptionStack());
                        }else{
                            clientLog.setServerExceptionStr(serverLog.getServerExceptionStr());
                        }
                        System.out.println("1");
                    }
                    logVO.addTimeLine(rpcVO.getValue().getStartDate(), rpcVO.getValue().getCost());
                } else {
                    traceLogMap.put(colId, rpcVO.getValue());
                }
            }
        }
    }
}
