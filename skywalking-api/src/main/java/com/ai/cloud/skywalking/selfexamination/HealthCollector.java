package com.ai.cloud.skywalking.selfexamination;

import java.util.HashMap;
import java.util.Map;

import static com.ai.cloud.skywalking.conf.Config.SkyWalking.USER_ID;
import com.ai.cloud.skywalking.util.BuriedPointMachineUtil;

public class HealthCollector extends Thread{
	private static Map<String, HeathReading> heathReadings = new HashMap<String, HeathReading>();
	
	public static HeathReading getCurrentHeathReading(String extraId){
		String id = getId(extraId);
		if(!heathReadings.containsKey(id)){
			synchronized (heathReadings) {
				if(!heathReadings.containsKey(id)){
					heathReadings.put(id, new HeathReading(id));
				}
			}
		}
		return heathReadings.get(id);
	}
	
	private static String getId(String extraId){
		return  "SDK,U:" + USER_ID + ",M:" + BuriedPointMachineUtil.getHostDesc() +",P:" + BuriedPointMachineUtil.getProcessNo() + ",T:"
				+ Thread.currentThread().getName() + "("
				+ Thread.currentThread().getId() + ")" + (extraId == null? "" : ",extra:" + extraId);
	}
	
	@Override
	public void run(){
		//TODO: 使用专有的端口，将数据上报给服务端，定时上报，默认应为分钟级别，降低服务端压力
	}
}
