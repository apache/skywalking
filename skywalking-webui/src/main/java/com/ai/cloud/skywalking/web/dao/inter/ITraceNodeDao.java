package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.bo.TraceNodeInfo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Created by xin on 16-3-30.
 */
public interface ITraceNodeDao {

    Map<String, TraceNodeInfo> queryTraceNodesByTraceId(String traceId) throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;
}
