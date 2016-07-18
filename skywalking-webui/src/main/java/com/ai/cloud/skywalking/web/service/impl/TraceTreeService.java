package com.ai.cloud.skywalking.web.service.impl;

import com.ai.cloud.skywalking.protocol.util.SpanLevelIdComparators;
import com.ai.cloud.skywalking.web.dao.inter.ITraceNodeDao;
import com.ai.cloud.skywalking.web.dto.TraceNodeInfo;
import com.ai.cloud.skywalking.web.dto.TraceNodesResult;
import com.ai.cloud.skywalking.web.dto.TraceTreeInfo;
import com.ai.cloud.skywalking.web.service.inter.ITraceTreeService;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.util.ReplaceAddressUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by xin on 16-3-30.
 */
@Service
@Transactional
public class TraceTreeService implements ITraceTreeService {

    @Autowired
    private ITraceNodeDao traceTreeDao;

    @Override
    public TraceTreeInfo queryTraceTreeByTraceId(String traceId) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException {
        TraceTreeInfo traceTreeInfo = new TraceTreeInfo(traceId);
        TraceNodesResult traceNodesResult = traceTreeDao.queryTraceNodesByTraceId(traceId);
        List<TraceNodeInfo> traceNodeInfoList = traceNodesResult.getResult();
        if (traceNodesResult.isOverMaxQueryNodeNumber()) {
            traceNodeInfoList = new ArrayList<TraceNodeInfo>();
            traceNodeInfoList.addAll(traceTreeDao.queryEntranceNodeByTraceId(traceId));
            traceTreeInfo.setRealNodeSize(Constants.MAX_SEARCH_SPAN_SIZE + 1);
        } else {
            traceTreeInfo.setRealNodeSize(traceNodeInfoList.size());
        }

        if (traceNodeInfoList.size() > 0) {
            final List<Long> endTime = new ArrayList<Long>();
            endTime.add(0, traceNodeInfoList.get(0).getEndDate());


            Collections.sort(traceNodeInfoList, new Comparator<TraceNodeInfo>() {
                @Override
                public int compare(TraceNodeInfo arg0, TraceNodeInfo arg1) {
                    if (endTime.get(0) < arg0.getEndDate()) {
                        endTime.set(0, arg0.getEndDate());
                    }
                    if (endTime.get(0) < arg1.getEndDate()) {
                        endTime.set(0, arg1.getEndDate());
                    }
                    return SpanLevelIdComparators.ascCompare(arg0.getColId(), arg1.getColId());
                }
            });

            // 截断
            int subIndex = traceNodeInfoList.size();
            if (subIndex > Constants.MAX_SHOW_SPAN_SIZE) {
                subIndex = Constants.MAX_SHOW_SPAN_SIZE;
            }
            traceTreeInfo.setHasBeenSpiltNodes(traceNodeInfoList.subList(0, subIndex));
            traceTreeInfo.setBeginTime(traceNodeInfoList.get(0).getStartDate());
            traceTreeInfo.setEndTime(endTime.get(0));
            if (traceNodeInfoList.get(0) != null) {
                traceTreeInfo.fillCallChainTreeToken(ReplaceAddressUtil.replace(traceNodeInfoList.get(0).getViewPointId(),
                        traceNodeInfoList.get(0).getApplicationId()));
            }
        }

        return traceTreeInfo;
    }
}
