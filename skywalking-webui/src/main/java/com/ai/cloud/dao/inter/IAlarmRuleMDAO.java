package com.ai.cloud.dao.inter;

import com.ai.cloud.vo.mvo.AlarmRuleMVO;

public interface IAlarmRuleMDAO {

	public AlarmRuleMVO queryUserDefaultAlarmRule(AlarmRuleMVO rule);

	public AlarmRuleMVO queryAppAlarmRule(AlarmRuleMVO ruleMVO);
	
	public AlarmRuleMVO createAlarmRule(AlarmRuleMVO ruleMVO);

	public AlarmRuleMVO modifyAlarmRule(AlarmRuleMVO ruleMVO);
	
}
