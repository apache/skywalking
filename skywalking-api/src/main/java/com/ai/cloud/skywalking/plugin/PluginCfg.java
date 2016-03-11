package com.ai.cloud.skywalking.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import com.ai.cloud.skywalking.util.StringUtil;

public class PluginCfg {
	public final static PluginCfg CFG = new PluginCfg();
	
	private Set<String> interceptorClassList = new HashSet<String>();
	
	private PluginCfg(){}
	
	void load(InputStream input) throws IOException{
		try{
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			String nhanceOriginClassName = null;
			while((nhanceOriginClassName = reader.readLine()) != null){
				if(!StringUtil.isEmpty(nhanceOriginClassName)){
					interceptorClassList.add(nhanceOriginClassName.trim());
				}
			}
		}finally{
			input.close();
		}
	}
	
	public Set<String> getInterceptorClassList(){
		return interceptorClassList;
	}
}
