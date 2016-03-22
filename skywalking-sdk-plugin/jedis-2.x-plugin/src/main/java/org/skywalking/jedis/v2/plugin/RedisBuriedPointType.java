package org.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.CallType;

public class RedisBuriedPointType implements IBuriedPointType {
	 private static RedisBuriedPointType redisBuriedPointType;
	 
	 public static IBuriedPointType instance() {
	        if (redisBuriedPointType == null) {
	        	redisBuriedPointType = new RedisBuriedPointType();
	        }

	        return redisBuriedPointType;
	    }
	

	@Override
	public String getTypeName() {
		return "Redis";
	}

	@Override
	public CallType getCallType() {
		return CallType.SYNC;
	}

	private RedisBuriedPointType(){}
}
