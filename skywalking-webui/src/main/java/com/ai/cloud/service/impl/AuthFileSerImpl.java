package com.ai.cloud.service.impl;

import com.ai.cloud.dao.inter.IAuthConfigMDAO;
import com.ai.cloud.service.inter.IAuthFileSer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class AuthFileSerImpl implements IAuthFileSer {

    @Autowired
    private IAuthConfigMDAO authConfigMDAO;

    @Override
    public Properties queryAuthFile(String authType) {
        return authConfigMDAO.queryAllAuthConfig(authType);
    }
}
