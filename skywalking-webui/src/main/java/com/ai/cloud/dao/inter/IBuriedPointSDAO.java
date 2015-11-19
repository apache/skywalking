/**
 * 
 */
package com.ai.cloud.dao.inter;

import java.io.IOException;
import java.util.Map;

import com.ai.cloud.vo.mvo.BuriedPointEntry;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午3:21:49
 * @version V0.1
 */
public interface IBuriedPointSDAO {
	
	public Map<String, BuriedPointEntry> queryLogByTraceId(String tableName, String traceId) throws IOException;
	
}
