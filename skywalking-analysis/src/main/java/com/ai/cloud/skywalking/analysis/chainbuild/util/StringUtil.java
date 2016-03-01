package com.ai.cloud.skywalking.analysis.chainbuild.util;

public class StringUtil {
	public static boolean isBlank(String str){
		if(str == null || str == "" || str.trim() == ""){
			return true;
		}else{
			return false;
		}
	}
	
	public static boolean equal(String str1, String str2){
		if(str1 == null){
			str1 = "";
		}
		if(str2 == null){
			str2 = "";
		}
		
		return str1.trim().equals(str2.trim());
	}
}
