/**
 * 
 */
package com.ai.cloud.service.inter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.ai.cloud.vo.mvo.TraceLogEntry;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午5:56:04
 * @version V0.1
 */
public interface IQueryTraceLogSer {
	
	public Map<String, TraceLogEntry> queryLogByTraceId(String traceId) throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;
	
}
