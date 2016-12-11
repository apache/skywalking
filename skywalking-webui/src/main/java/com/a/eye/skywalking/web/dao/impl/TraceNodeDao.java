package com.a.eye.skywalking.web.dao.impl;

import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.util.StringUtil;
import com.a.eye.skywalking.web.client.routing.SearchClient;
import com.a.eye.skywalking.web.dao.inter.ITraceNodeDao;
import com.a.eye.skywalking.web.dto.TraceNodeInfo;
import com.a.eye.skywalking.web.dto.TraceNodesResult;
import com.a.eye.skywalking.web.util.Constants;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by xin on 16-3-30.
 */
@Repository
public class TraceNodeDao implements ITraceNodeDao {

    @Override
    public TraceNodesResult queryTraceNodesByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        return new SearchClient().searchSpan(traceId);
    }

    // TODO: 2016/12/10
    private Collection<TraceNodeInfo> generateTestData() {
        long startTime = System.currentTimeMillis();
        TraceId traceId = TraceId.newBuilder()
                .addSegments(202016)
                .addSegments(startTime)
                .addSegments(-1)
                .addSegments(1234)
                .addSegments(1234)
                .addSegments(1234).build();
        Span rootSpan = Span.newBuilder().setSpanType(1).setAddress("127.0.0.1/ascrutae").setApplicationCode("test")
                .setCallType("S").setCost(200).setLevelId(0).setViewpoint("provider://127.0.0.1:20880/com.a.eye.dubbo.provider.GreetService.sayHello()")
                .setProcessNo(19872)
                .setTraceId(traceId).setStartTime(startTime)
                .setStatusCode(0).setUsername("test").build();

        Span span = Span.newBuilder().setSpanType(1).setAddress("127.0.0.1/ascrutae").setApplicationCode("test")
                .setCallType("S").setCost(100).setParentLevelId("0").setLevelId(0).setViewpoint("consumer://127.0.0.1:20880/com.a.eye.dubbo.provider.GreetService.sayHello()")
                .setProcessNo(19872)
                .setTraceId(traceId).setStartTime(startTime + 50)
                .setStatusCode(0).setUsername("test").build();

        List<TraceNodeInfo> nodeInfos = new ArrayList<>();
        nodeInfos.add(new TraceNodeInfo(rootSpan));
        nodeInfos.add(new TraceNodeInfo(span));

        return nodeInfos;
    }

    private void computeRPCInfo(Map<String, TraceNodeInfo> rpcMap, Map<String, TraceNodeInfo> traceLogMap) {
        // 合并处理
        if (rpcMap.size() > 0) {
            for (Map.Entry<String, TraceNodeInfo> rpcVO : rpcMap.entrySet()) {
                String colId = rpcVO.getKey();
                if (traceLogMap.containsKey(colId)) {
                    TraceNodeInfo logVO = traceLogMap.get(colId);
                    TraceNodeInfo serverLog = rpcVO.getValue();
                    if (StringUtil.isEmpty(logVO.getStatusCodeStr()) || Constants.STATUS_CODE_9.equals(logVO.getStatusCodeStr())) {
                        //serverLog.setColId(colId);
                        traceLogMap.put(colId, serverLog);
                    } else {
                        TraceNodeInfo clientLog = traceLogMap.get(colId);
                        clientLog.setApplicationIdStr(clientLog.getApplicationIdStr() + " --> " + serverLog.getApplicationIdStr());
                        clientLog.setViewPointId(serverLog.getViewPointId());
                        clientLog.setViewPointIdSub(serverLog.getViewPointIdSub());
                        clientLog.setAddress(serverLog.getAddress());
                        if (StringUtil.isEmpty(clientLog.getExceptionStack())) {
                            clientLog.setExceptionStack(serverLog.getExceptionStack());
                        } else {
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
