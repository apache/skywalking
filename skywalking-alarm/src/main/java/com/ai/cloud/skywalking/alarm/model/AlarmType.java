package com.ai.cloud.skywalking.alarm.model;


public class AlarmType {

	//告警类型名称
	private String type;
	//告警标签
	private String label;
	//告警描述
	private String desc;
	
	@Override
	public String toString() {
		return "AlarmType [type=" + type + ", label=" + label + ", desc=" + desc + "]";
	}	
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}	
}
