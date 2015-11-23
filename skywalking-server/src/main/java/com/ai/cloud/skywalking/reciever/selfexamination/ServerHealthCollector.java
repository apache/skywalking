package com.ai.cloud.skywalking.reciever.selfexamination;

import java.util.HashMap;
import java.util.Map;

import com.ai.cloud.skywalking.reciever.util.MachineUtil;

public class ServerHealthCollector extends Thread{
	private static Map<String, ServerHeathReading> heathReadings = new HashMap<String, ServerHeathReading>();
	
	public static ServerHeathReading getCurrentHeathReading(String extraId){
		String id = getId(extraId);
		if(!heathReadings.containsKey(id)){
			synchronized (heathReadings) {
				if(!heathReadings.containsKey(id)){
					heathReadings.put(id, new ServerHeathReading(id));
				}
			}
		}
		return heathReadings.get(id);
	}
	
	private static String getId(String extraId){
		return  "SkyWalkingServer,M:" + MachineUtil.getHostDesc() +",P:" + MachineUtil.getProcessNo() + ",T:"
				+ Thread.currentThread().getName() + "("
				+ Thread.currentThread().getId() + ")" + (extraId == null? "" : ",extra:" + extraId);
	}
	
	@Override
	public void run(){
		//TODO: 服务端本地存储，用于将信息存储如数据库，并供前台展现，完成定时刷新
	}
}
