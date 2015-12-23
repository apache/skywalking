package com.ai.cloud.service.inter;

import com.ai.cloud.vo.mvo.SystemConfigMVO;

public interface ISystemConfigSer {

    SystemConfigMVO querySystemConfigByKey(String key);
}
