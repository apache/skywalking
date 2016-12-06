package com.a.eye.skywalking.web.dao.inter;

import com.a.eye.skywalking.web.dto.TraceNodesResult;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by xin on 16-3-30.
 */
public interface ITraceNodeDao {

    TraceNodesResult queryTraceNodesByTraceId(String traceId)
            throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;
}
