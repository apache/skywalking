package com.a.eye.skywalking.web.dao.inter;

import com.a.eye.skywalking.web.dto.TraceNodesResult;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.web.dto.TraceNodeInfo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

/**
 * Created by xin on 16-3-30.
 */
public interface ITraceNodeDao {

    TraceNodesResult queryTraceNodesByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            ConvertFailedException;

    Collection<TraceNodeInfo> queryEntranceNodeByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            ConvertFailedException;
}
