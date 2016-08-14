package com.a.eye.skywalking.alarm.model;

import com.a.eye.skywalking.alarm.util.RedisUtil;
import com.google.gson.Gson;

import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;

public class AlarmRule {
    private ConfigArgsDescriber configArgsDescriber;

    private String configArgs;
    private String todoType;
    private String ruleId;
    private boolean global = false;
    private long previousFireTimeM;
    private String uid;

    public AlarmRule(String uid, String ruleId) {
        this.ruleId = ruleId;
        this.uid = uid;
        previousFireTimeM = getPreviousFireTime(uid, ruleId);
    }

    public void setConfigArgs(String configArgs) {
        this.configArgs = configArgs;
        configArgsDescriber = new Gson().fromJson(configArgs, ConfigArgsDescriber.class);
    }

    private List<ApplicationInfo> applicationInfos = new ArrayList<ApplicationInfo>();

    public List<ApplicationInfo> getApplicationInfos() {
        return applicationInfos;
    }

    public void setApplicationInfos(List<ApplicationInfo> applicationInfos) {
        this.applicationInfos = applicationInfos;
    }

    public ConfigArgsDescriber getConfigArgsDescriber() {
        return configArgsDescriber;
    }

    public void setConfigArgsDescriber(ConfigArgsDescriber configArgsDescriber) {
        this.configArgsDescriber = configArgsDescriber;
    }

    public void setTodoType(String todoType) {
        this.todoType = todoType;
    }

    public String getTodoType() {
        return todoType;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public boolean isGlobal() {
        return global;
    }

    public long getPreviousFireTimeM() {
        return previousFireTimeM;
    }

    public void setPreviousFireTimeM(long previousFireTimeM) {
        this.previousFireTimeM = previousFireTimeM;
    }

    private static long getPreviousFireTime(final String userId, final String ruleId) {
    	return RedisUtil.execute(new RedisUtil.Executable<Long>() {
			@Override
			public Long exe(Jedis client) {
				String previousTime = client.get(userId + "-" + ruleId);
	            if (previousTime == null || previousTime.length() <= 0) {
	                return System.currentTimeMillis() / (10000 * 6);
	            }
	            return Long.valueOf(previousTime);
			}
		});
    }
}
