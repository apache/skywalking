package com.ai.cloud.vo.mvo;

public class UserInfoMVO {
	private String uid;
	
	private String userName;
	
	private String password;

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

	@Override
	public String toString() {
		return "UserInfoMVO [uid=" + uid + ", userName=" + userName + ", password=" + password + "]";
	}
}
