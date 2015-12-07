package com.ai.cloud.service.impl;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ai.cloud.dao.impl.ApplicationMDAO;
import com.ai.cloud.dao.inter.IApplicationMDAO;
import com.ai.cloud.service.inter.IApplicationSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.vo.mvo.ApplicationInfoMVO;
import com.ai.cloud.vo.svo.ApplicationInfoSVO;
import com.alibaba.fastjson.JSONObject;

@Service
public class ApplicationSerImpl implements IApplicationSer {

	@Autowired
	IApplicationMDAO applicationMDAO;

	private static Logger logger = LogManager.getLogger(ApplicationSerImpl.class);

	@Override
	public List<ApplicationInfoMVO> queryUserAppListByUid(String uid) {
		return applicationMDAO.queryAppListByUid(uid);
	}

	@Override
	public String createApplicationInfo(ApplicationInfoSVO appSVO) {

		JSONObject reJson = new JSONObject();

		List<ApplicationInfoMVO> appList = applicationMDAO.queryUserAppListByAppCode(appSVO);
		if(appList != null && appList.size()>0){
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "当前用户下存在相同应用名");
			return reJson.toJSONString();
		}

		try {
			applicationMDAO.addApplicationInfo(appSVO);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "创建应用信息成功");
		} catch (Exception e) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "创建应用信息异常");
			e.printStackTrace();
			return reJson.toJSONString();
		}
		return reJson.toJSONString();
	}

	@Override
	public String delete(ApplicationInfoSVO appSVO) {
		JSONObject reJson = new JSONObject();

		try {
			applicationMDAO.deleteAppInfoById(appSVO);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "删除应用信息成功");
		} catch (Exception e) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "删除应用信息异常");
			e.printStackTrace();
			return reJson.toJSONString();
		}
		return reJson.toJSONString();
	}

}
