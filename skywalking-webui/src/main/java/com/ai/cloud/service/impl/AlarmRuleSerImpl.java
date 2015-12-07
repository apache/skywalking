package com.ai.cloud.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ai.cloud.dao.inter.IAlarmRuleMDAO;
import com.ai.cloud.service.inter.IAlarmRuleSer;
import com.ai.cloud.vo.mvo.AlarmRuleMVO;

@Service
public class AlarmRuleSerImpl implements IAlarmRuleSer {

	@Autowired
	IAlarmRuleMDAO alarmRuleMDAO;

	private static Logger logger = LogManager.getLogger(ApplicationSerImpl.class);

	@Override
	public AlarmRuleMVO queryUserDefaultAlarmRule(AlarmRuleMVO rule) {
		return alarmRuleMDAO.queryUserDefaultAlarmRule(rule);
	}

	@Override
	public AlarmRuleMVO queryAppAlarmRule(AlarmRuleMVO ruleMVO) {
		return alarmRuleMDAO.queryAppAlarmRule(ruleMVO);
	}

	@Override
	public AlarmRuleMVO createAlarmRule(AlarmRuleMVO ruleMVO) {
		return alarmRuleMDAO.createAlarmRule(ruleMVO);
	}

	@Override
	public AlarmRuleMVO modifyAlarmRule(AlarmRuleMVO ruleMVO) {
		return alarmRuleMDAO.modifyAlarmRule(ruleMVO);
	}

}
