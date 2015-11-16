/**
 * 
 */
package com.ai.cloud.util.common;

/**
 * 字符串处理类
 * 
 * @author tz
 * @date 2015年11月10日 下午2:35:02
 * @version V0.3
 */
public class StringUtil {

	/**
	 * 
	 * @param str
	 * @return
	 */
	public static boolean isBlank(String str) {
		if (null == str) {
			return true;
		}
		if ("".equals(str.trim())) {
			return true;
		}
		return false;
	}
}
