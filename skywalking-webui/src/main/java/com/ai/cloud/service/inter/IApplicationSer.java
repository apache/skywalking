package com.ai.cloud.service.inter;

import java.util.List;

import com.ai.cloud.vo.mvo.ApplicationInfoMVO;
import com.ai.cloud.vo.svo.ApplicationInfoSVO;

/**
 * 
 * @author tz
 * @date 2015年11月18日 下午5:56:04
 * @version V0.1
 */
public interface IApplicationSer {
	
	public List<ApplicationInfoMVO> queryUserAppListByUid(String uid);

	public String createApplicationInfo(ApplicationInfoSVO appSVO);

	public String delete(ApplicationInfoSVO appSVO);

}
