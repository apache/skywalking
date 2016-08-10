package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;
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

    private String CALL_CHAIN_TABLE_NAME = "trace-data";

    @Autowired
    private HBaseUtils hBaseUtils;


    @Override
    public TraceNodesResult queryTraceNodesByTraceId(String traceId)
            throws ConvertFailedException, IOException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf(CALL_CHAIN_TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        g.setFilter(new ColumnCountGetFilter(Constants.MAX_SEARCH_SPAN_SIZE + 1));
        Result r = table.get(g);
        Map<String, TraceNodeInfo> traceLogMap = new HashMap<String, TraceNodeInfo>();
        Map<String, TraceNodeInfo> rpcMap = new HashMap<String, TraceNodeInfo>();
        TraceNodesResult result = new TraceNodesResult();
        if (r.rawCells().length < Constants.MAX_SEARCH_SPAN_SIZE) {
            SpanDataHandler spanDataHandler = new SpanDataHandler();
            for (Cell cell : r.rawCells()) {
                spanDataHandler.addSpan(cell);
            }

            for (Map.Entry<String, TraceNodeInfo> entry : spanDataHandler.merge().entrySet()){
                SortUtil.addCurNodeTreeMapKey(traceLogMap, entry.getKey(), entry.getValue());
            }
            computeRPCInfo(rpcMap, traceLogMap);
            result.setOverMaxQueryNodeNumber(false);
            result.setResult(traceLogMap.values());
        }else{
            result.setOverMaxQueryNodeNumber(true);
        }
        return result;
    }

    private static final String[] NODES = new String[] {"0","0-ACK","0.0","0.0-ACK"};

    @Override
    public Collection<TraceNodeInfo> queryEntranceNodeByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            ConvertFailedException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf(CALL_CHAIN_TABLE_NAME));
        Get g = new Get(Bytes.toBytes(traceId));
        g.addColumn("call-chain".getBytes(), "0".getBytes());
        g.addColumn("call-chain".getBytes(), "0.0".getBytes());
        g.addColumn("call-chain".getBytes(), "0-ACK".getBytes());
        g.addColumn("call-chain".getBytes(), "0.0-ACK".getBytes());
        Result r = table.get(g);

        Map<String, TraceNodeInfo> traceLogMap = new HashMap<String, TraceNodeInfo>();
        Map<String, TraceNodeInfo> rpcMap = new HashMap<String, TraceNodeInfo>();
        SpanDataHandler spanDataHandler = new SpanDataHandler();
        for (String node : NODES) {
            Cell cell = r.getColumnLatestCell("call-chain".getBytes(), node.getBytes());
            spanDataHandler.addSpan(cell);
        }

        for (Map.Entry<String, TraceNodeInfo> entry : spanDataHandler.merge().entrySet()){
            SortUtil.addCurNodeTreeMapKey(traceLogMap, entry.getKey(), entry.getValue());
        }

        computeRPCInfo(rpcMap, traceLogMap);
        return traceLogMap.values();
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
                    }
                    logVO.addTimeLine(rpcVO.getValue().getStartDate(), rpcVO.getValue().getCost());
                } else {
                    traceLogMap.put(colId, rpcVO.getValue());
                }
            }
        }
    }
}
