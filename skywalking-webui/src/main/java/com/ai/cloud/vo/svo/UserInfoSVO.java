package com.ai.cloud.vo.svo;

import java.sql.Timestamp;

public class UserInfoSVO {
	private String uid;
	
	private String userName;
	
	private String password;
	
	private String roleType;
	
	private Timestamp createTime;
	
	private String sts;
	
	private Timestamp modifyTime;

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRoleType() {
		return roleType;
	}

	public void setRoleType(String roleType) {
		this.roleType = roleType;
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
		return "UserInfoSVO [uid=" + uid + ", userName=" + userName + ", password=" + password + ", roleType="
				+ roleType + ", createTime=" + createTime + ", sts=" + sts + ", modifyTime=" + modifyTime + "]";
	}

}
