/**
 * 
 */
package com.ai.cloud.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 常量类
 * 
 * @author tz
 * @date 2015年11月10日 下午2:51:25
 * @version V0.1
 */
public class Constants {

	public static final String VERSION_STR = "version";

	public static final String VERSION_VAL = "0.1";

	/** hbase集群 */
	public static final String QUORUM = "10.1.235.197,10.1.235.198,10.1.235.199";
	/** zk端口 */
	public static final String CLIENT_PORT = "29181";
	/** hbase表名 */
	public static final String TABLE_NAME_CHAIN = "sw-call-chain";
	/** 层级分割符 */
	public static final char VAL_SPLIT_CHAR = '.';
	/** RPC远端调用节点结束标识 */
	public static final String RPC_END_FLAG = "-S";

	public static final String SPAN_TYPE_M = "M";
	public static final String SPAN_TYPE_J = "J";
	public static final String SPAN_TYPE_W = "W";
	public static final String SPAN_TYPE_D = "D";
	public static final String SPAN_TYPE_U = "U";
	/** SPAN_TYPE码表 */
	public static Map<String, String> SPAN_TYPE_MAP = new HashMap<String, String>() {
		{
			put("M", "JAVA");
			put("J", "JDBC");
			put("W", "WEB");
			put("D", "DUBBO");
			put("U", "UNKNOWN");
		}
	};

	public static final String STATUS_CODE_0 = "0";
	public static final String STATUS_CODE_1 = "1";
	public static final String STATUS_CODE_9 = "9";
	/** STATUS_CODE码表 */
	public static Map<String, String> STATUS_CODE_MAP = new HashMap<String, String>() {
		{
			put("0", "OK");
			put("1", "FAIL");
			put("9", "MISSING");
		}
	};
	
	public static final String JSON_RESULT_KEY_RESULT = "result";
	public static final String JSON_RESULT_KEY_RESULT_OK = "OK";
	public static final String JSON_RESULT_KEY_RESULT_FAIL = "FAIL";
	
	public static final String JSON_RESULT_KEY_RESULT_MSG = "msg";
	public static final String JSON_RESULT_KEY_RESULT_DATA = "data";
	
	public static final String ROLE_TYPE_USER = "user";
	public static final String ROLE_TYPE_ADMIN = "admin";
	
	public static final String STR_VAL_A = "A";
	public static final String STR_VAL_P = "P";
	
	public static final String IS_GLOBAL_FALG_0 = "0";
	public static final String IS_GLOBAL_FALG_1 = "1";

}
