package com.ai.cloud.skywalking.selfexamination;

import java.util.HashMap;
import java.util.Map;

import com.ai.cloud.skywalking.conf.Constants;

public class HeathReading {
	public static final String ERROR = "ERROR";
	public static final String WARNING = "WARNING";
	public static final String INFO = "INFO";
	
	private String id;
	
	private Map<String, HeathDetailData> datas = new HashMap<String, HeathDetailData>();
	
	/**
	 * 健康读数，只应该在工作线程中创建
	 * 
	 */
	public HeathReading(String id) {
		this.id = id;
	}
	
	public void updateData(String key, String newData){
		if(datas.containsKey(key)){
			datas.get(key).updateData(newData);
		}else{
			datas.put(key, new HeathDetailData(newData));
		}
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(this.id);
		sb.append(Constants.HEALTH_DATA_SPILT_PATTERN);
		for(Map.Entry<String, HeathDetailData> data : datas.entrySet()){
			sb.append(data.getKey()).append(Constants.HEALTH_DATA_SPILT_PATTERN).append(data.getValue().toString()).append(Constants.HEALTH_DATA_SPILT_PATTERN);
		}
		
		//reset data
		datas = new HashMap<String, HeathReading.HeathDetailData>();
		return sb.toString();
	}
	
	class HeathDetailData{
		private String data;
		
		private long statusTime;
		
		HeathDetailData(String initialData){
			data = initialData;
			statusTime = System.currentTimeMillis();
		}
		
		void updateData(String newData){
			data = newData;
			statusTime = System.currentTimeMillis();
		}

		String getData() {
			return data;
		}

		long getStatusTime() {
			return statusTime;
		}
		
		@Override
		public String toString(){
			return "d:" + data + Constants.HEALTH_DATA_SPILT_PATTERN + "t:" + statusTime;
		}
	}
}
