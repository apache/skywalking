package com.ai.cloud.skywalking.example.account.manage;

import com.ai.cloud.skywalking.example.account.dubbo.interfaces.param.AccountInfo;
import com.ai.cloud.skywalking.example.account.exception.BusinessException;

public interface IAccountMaintainService {
    boolean createAccount(AccountInfo accountInfo) throws BusinessException;
}
