package com.ai.cloud.vo.svo;

import java.sql.Timestamp;

public class ApplicationInfoSVO {
	
	private String appId;
	
	private String uid;
	
	private String appCode;
	
	private Timestamp createTime;
	
	private String sts;

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public String getAppCode() {
		return appCode;
	}

	public void setAppCode(String appCode) {
		this.appCode = appCode;
	}

	public Timestamp getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Timestamp createTime) {
		this.createTime = createTime;
	}

	public String getSts() {
		return sts;
	}

	public void setSts(String sts) {
		this.sts = sts;
	}

	@Override
	public String toString() {
		return "ApplicationInfoSVO [appId=" + appId + ", uid=" + uid + ", appCode=" + appCode + ", createTime="
				+ createTime + ", sts=" + sts + "]";
	}
}
