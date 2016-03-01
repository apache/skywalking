package com.ai.cloud.skywalking.analysis.chainbuild.util;

/**
 * 版本识别器
 * 
 * @author wusheng
 *
 */
public class VersionIdentifier {
	/**
	 * 根据tid识别数据是否可分析<br/>
	 * 目前允许分析所有1.x的版本号
	 * 
	 * @param tid
	 * @return
	 */
	public static boolean enableAnaylsis(String tid){
		if(tid != null){
			String[] tidSections = tid.split("\\.");
			if(tidSections.length == 7){
				String version = tidSections[0];
				String subVersion = tidSections[1];
				
				if("1".equals(version) && subVersion.length() > 0){
					return true;
				}
			}
		}
		return false;
	}
}
