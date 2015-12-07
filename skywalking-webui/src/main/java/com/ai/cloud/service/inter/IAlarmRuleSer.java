package com.ai.cloud.service.inter;

import com.ai.cloud.vo.mvo.AlarmRuleMVO;

public interface IAlarmRuleSer {
	
	public AlarmRuleMVO queryUserDefaultAlarmRule(AlarmRuleMVO rule);

	public AlarmRuleMVO queryAppAlarmRule(AlarmRuleMVO ruleMVO);
	
	public AlarmRuleMVO createAlarmRule(AlarmRuleMVO ruleMVO);

	public AlarmRuleMVO modifyAlarmRule(AlarmRuleMVO ruleMVO);
	
}
