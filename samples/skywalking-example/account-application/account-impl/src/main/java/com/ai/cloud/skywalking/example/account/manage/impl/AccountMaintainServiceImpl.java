package com.ai.cloud.skywalking.example.account.manage.impl;

import com.ai.cloud.skywalking.example.account.dao.mapper.bo.TAccount;
import com.ai.cloud.skywalking.example.account.dao.mapper.interfaces.TAccountMapper;
import com.ai.cloud.skywalking.example.account.dubbo.interfaces.param.AccountInfo;
import com.ai.cloud.skywalking.example.account.exception.BusinessException;
import com.ai.cloud.skywalking.example.account.manage.IAccountMaintainService;
import com.ai.cloud.skywalking.plugin.spring.Tracing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountMaintainServiceImpl implements IAccountMaintainService {

    private Logger logger = LogManager.getLogger(AccountMaintainServiceImpl.class);

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Override
    @Tracing
    public boolean createAccount(AccountInfo accountInfo) throws BusinessException {
        if (accountInfo.getPhoneNumber() == null || accountInfo.getPhoneNumber().length() <= 0) {
            throw new BusinessException("Phone number cannot be null");
        }
        if (accountInfo.getPhoneNumber() == null || accountInfo.getPhoneNumber().length() <= 0) {
            throw new BusinessException("Phone number cannot be null");
        }
        try {
            TAccountMapper tAccountMapper = sqlSessionTemplate.getMapper(TAccountMapper.class);
            TAccount tAccount = new TAccount();
            tAccount.setPhoneNumber(accountInfo.getPhoneNumber());
            tAccount.setMail(accountInfo.getMail());
            tAccountMapper.insert(tAccount);
            return true;
        } catch (Exception e) {
            logger.error("Create account failed", e);
            throw new BusinessException("Create account failed", e);
        }
    }
}
