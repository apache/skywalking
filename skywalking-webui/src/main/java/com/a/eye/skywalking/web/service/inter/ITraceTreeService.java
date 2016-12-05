package com.a.eye.skywalking.web.service.inter;

import com.a.eye.skywalking.web.dto.TraceTreeInfo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by xin on 16-3-30.
 */
public interface ITraceTreeService {
    TraceTreeInfo queryTraceTreeByTraceId(String traceId) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException;
}
