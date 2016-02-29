package com.ai.cloud.skywalking.selfexamination;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.BuriedPointMachineUtil;

public class SDKHealthCollector extends Thread {
	private Logger logger = LogManager.getLogger(SDKHealthCollector.class);

	private static Map<String, HeathReading> heathReadings = new ConcurrentHashMap<String, HeathReading>();

	private SDKHealthCollector(){
		super("HealthCollector");
	}
	
	public static void init(){
		if(AuthDesc.isAuth()){
			new SDKHealthCollector().start();
		}
	}
	
	public static HeathReading getCurrentHeathReading(String extraId) {
		String id = getId(extraId);
		if (!heathReadings.containsKey(id)) {
			synchronized (heathReadings) {
				if (!heathReadings.containsKey(id)) {
					heathReadings.put(id, new HeathReading(id));
				}
			}
		}
		return heathReadings.get(id);
	}

	private static String getId(String extraId) {
		return "SDK-API,M:" + BuriedPointMachineUtil.getHostDesc() + ",P:"
				+ BuriedPointMachineUtil.getProcessNo() + ",T:"
				+ Thread.currentThread().getName() + "("
				+ Thread.currentThread().getId() + ")"
				+ (extraId == null ? "" : ",extra:" + extraId);
	}

	@Override
	public void run() {
		while (true) {
			try {
				Map<String, HeathReading> heathReadingsSnapshot = heathReadings;
				heathReadings = new ConcurrentHashMap<String, HeathReading>();
				String[] keyList = heathReadingsSnapshot.keySet().toArray(new String[0]);
				Arrays.sort(keyList);
				StringBuilder log = new StringBuilder();
				log.append("\n---------SDK Health Collector Report---------\n");
				for(String key : keyList){
					log.append(heathReadingsSnapshot.get(key)).append("\n");
				}
				log.append("------------------------------------------------\n");
				
				logger.info(log);
				
				try {
					Thread.sleep(Config.HealthCollector.REPORT_INTERVAL);
				} catch (InterruptedException e) {
					logger.warn("sleep error.", e);
				}
			} catch (Throwable t) {
				logger.error("SDKHealthCollector report error.", t);
			}
		}
	}
}
