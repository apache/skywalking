package com.a.eye.skywalking.web.dao.impl;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.web.dao.inter.ITraceNodeDao;
import com.a.eye.skywalking.web.dto.TraceNodeInfo;
import com.a.eye.skywalking.web.dto.TraceNodesResult;
import com.a.eye.skywalking.web.util.Constants;
import com.a.eye.skywalking.web.util.SortUtil;
import com.a.eye.skywalking.web.util.StringUtil;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xin on 16-3-30.
 */
@Repository
public class TraceNodeDao implements ITraceNodeDao {

    @Override
    public TraceNodesResult queryTraceNodesByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        List<Span> searchResult = new ArrayList<>();

        TraceNodesResult result = new TraceNodesResult();
        Map<String, TraceNodeInfo> traceNodeInfoMap = new HashMap<>();
        searchResult.forEach((span -> {
            traceNodeInfoMap.put(span.getLevelId(), new TraceNodeInfo(span));
        }));

        Map<String, TraceNodeInfo> traceLogMap = new HashMap<String, TraceNodeInfo>();
        Map<String, TraceNodeInfo> rpcMap = new HashMap<String, TraceNodeInfo>();
        traceNodeInfoMap.entrySet().forEach((entry -> {
            SortUtil.addCurNodeTreeMapKey(traceLogMap, entry.getKey(), entry.getValue());
        }));
        computeRPCInfo(rpcMap, traceLogMap);
        result.setResult(traceLogMap.values());
        return result;
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
