package com.ai.cloud.dao.inter;

import java.util.List;

import com.ai.cloud.vo.mvo.ApplicationInfoMVO;
import com.ai.cloud.vo.svo.ApplicationInfoSVO;

public interface IApplicationMDAO {
	
	public List<ApplicationInfoMVO> queryAppListByUid(String uid);

	public ApplicationInfoSVO addApplicationInfo(ApplicationInfoSVO appSVO);
	
	public List<ApplicationInfoMVO> queryUserAppListByAppCode(ApplicationInfoSVO appCode);

	public void deleteAppInfoById(ApplicationInfoSVO appSVO);
}
