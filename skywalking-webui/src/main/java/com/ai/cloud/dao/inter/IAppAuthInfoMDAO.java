package com.ai.cloud.dao.inter;

import com.ai.cloud.vo.mvo.AppAuthInfoMVO;

public interface IAppAuthInfoMDAO {

	AppAuthInfoMVO queryAppAuthInfo(AppAuthInfoMVO rule);

	AppAuthInfoMVO createAlarmRule(AppAuthInfoMVO ruleMVO);

	AppAuthInfoMVO modifyAlarmRule(AppAuthInfoMVO ruleMVO);

}
