/**
 * 
 */
package com.ai.cloud.dao.inter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.ai.cloud.vo.mvo.TraceLogEntry;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午3:21:49
 * @version V0.1
 */
public interface IBuriedPointSDAO {
	
	public Map<String, TraceLogEntry> queryLogByTraceId(String tableName, String traceId) throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;
	
}
