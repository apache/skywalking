package com.ai.cloud.skywalking.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.ai.cloud.skywalking.util.StringUtil;

public class PluginCfg {
	public final static PluginCfg CFG = new PluginCfg();
	
	private List<String> interceptorClassList = new ArrayList<String>();
	
	private PluginCfg(){}
	
	void load(InputStream input) throws IOException{
		try{
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			String interceptorDefineClassName = null;
			while((interceptorDefineClassName = reader.readLine()) != null){
				if(!StringUtil.isEmpty(interceptorDefineClassName)){
					interceptorClassList.add(interceptorDefineClassName.trim());
				}
			}
		}finally{
			input.close();
		}
	}
	
	public List<String> getInterceptorClassList(){
		return interceptorClassList;
	}
}
