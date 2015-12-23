package com.ai.cloud.dao.inter;

import com.ai.cloud.vo.mvo.SystemConfigMVO;

public interface ISystemConfigMDAO {
    SystemConfigMVO querySystemConfigByKey(String key);
}
