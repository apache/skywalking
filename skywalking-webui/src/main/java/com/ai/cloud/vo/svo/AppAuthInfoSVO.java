package com.ai.cloud.vo.svo;

import java.sql.Timestamp;

public class AppAuthInfoSVO {
	
	private String infoId;
	
	private String appId;
	
	private String authJson;
	
	private Timestamp createTime;
	
	private String sts;
	
	private Timestamp modifyTime;

	public String getInfoId() {
		return infoId;
	}

	public void setInfoId(String infoId) {
		this.infoId = infoId;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getAuthJson() {
		return authJson;
	}

	public void setAuthJson(String authJson) {
		this.authJson = authJson;
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

	public Timestamp getModifyTime() {
		return modifyTime;
	}

	public void setModifyTime(Timestamp modifyTime) {
		this.modifyTime = modifyTime;
	}

	@Override
	public String toString() {
		return "AppAuthInfo [infoId=" + infoId + ", appId=" + appId + ", authJson=" + authJson + ", createTime="
				+ createTime + ", sts=" + sts + ", modifyTime=" + modifyTime + "]";
	}
	
}
