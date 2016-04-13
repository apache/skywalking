package com.ai.cloud.skywalking.web.service.impl;

import com.ai.cloud.skywalking.web.bo.TraceNodeInfo;
import com.ai.cloud.skywalking.web.bo.TraceTreeInfo;
import com.ai.cloud.skywalking.web.dao.inter.ITraceNodeDao;
import com.ai.cloud.skywalking.web.service.inter.ITraceTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by xin on 16-3-30.
 */
@Service
public class TraceTreeService implements ITraceTreeService {

    @Autowired
    private ITraceNodeDao traceTreeDao;

    @Override
    public TraceTreeInfo queryTraceTreeByTraceId(String traceId) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException {

        TraceTreeInfo traceTreeInfo  = traceTreeDao.queryTraceNodesByTraceId(traceId);
        if (traceTreeInfo != null) {
            List<TraceNodeInfo> nodes = traceTreeInfo.getNodes();
            final List<Long> endTime = new ArrayList<Long>();
            endTime.add(0, nodes.get(0).getEndDate());
            Collections.sort(nodes, new Comparator<TraceNodeInfo>() {
                @Override
                public int compare(TraceNodeInfo arg0, TraceNodeInfo arg1) {
                    if (endTime.get(0) < arg0.getEndDate()) {
                        endTime.set(0, arg0.getEndDate());
                    }
                    if (endTime.get(0) < arg1.getEndDate()) {
                        endTime.set(0, arg1.getEndDate());
                    }
                    return arg0.getColId().compareTo(arg1.getColId());
                }
            });
            long beginTime = nodes.get(0).getStartDate();
            traceTreeInfo.setBeginTime(beginTime);
            traceTreeInfo.setEndTime(endTime.get(0));
        }

        return traceTreeInfo;
    }
}
