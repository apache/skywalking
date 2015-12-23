package com.ai.cloud.service.impl;

import com.ai.cloud.dao.inter.ISystemConfigMDAO;
import com.ai.cloud.service.inter.ISystemConfigSer;
import com.ai.cloud.vo.mvo.SystemConfigMVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigSerImpl implements ISystemConfigSer {

    @Autowired
    private ISystemConfigMDAO iSystemConfigMDAO;

    @Override
    public SystemConfigMVO querySystemConfigByKey(String key) {
        return iSystemConfigMDAO.querySystemConfigByKey(key);
    }
}
