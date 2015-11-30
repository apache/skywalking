package com.ai.cloud.skywalking.example.account.dubbo.impl;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.example.account.dubbo.interfaces.IAccountMaintain;
import com.ai.cloud.skywalking.example.account.dubbo.interfaces.param.AccountInfo;
import com.ai.cloud.skywalking.example.account.exception.BusinessException;
import com.ai.cloud.skywalking.example.account.manage.IAccountMaintainService;
import com.ai.cloud.skywalking.example.account.model.CommonsHttpResult;
import com.ai.cloud.skywalking.example.account.util.HttpClientUtil;
import com.ai.cloud.skywalking.model.Identification;
import com.alibaba.dubbo.config.annotation.Service;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Service
public class AccountMaintainImpl implements IAccountMaintain {

    private static final String MAIL_APPLICATION_DEPLOY_ADDRESS = "http://localhost:8080/mail-web";

    private static final String SEND_MAIL_URL = "/mail/send";

    @Autowired
    private IAccountMaintainService iAccountMaintainService;

    @Override
    public String create(AccountInfo accountInfo) {
        Gson gson = new Gson();
        CommonsHttpResult commonsHttpResult = new CommonsHttpResult();
        try {
            boolean result = iAccountMaintainService.createAccount(accountInfo);
            if (!result) {
                commonsHttpResult.setErrorCode("99999");
                commonsHttpResult.setMessage("Create account failed");
            } else {
                Map<String, String> headParameters = new HashMap<String, String>();
                headParameters.put("SkyWalking-TRACING-NAME", new RPCBuriedPointSender().beforeSend(Identification.newBuilder().
                        spanType('W').viewPoint(MAIL_APPLICATION_DEPLOY_ADDRESS + SEND_MAIL_URL).build()).toString());
                Map<String, String> parameters = new HashMap<String, String>();
                parameters.put("recipientAccount", accountInfo.getMail());
                String mailResult = HttpClientUtil.sendPostRequest(MAIL_APPLICATION_DEPLOY_ADDRESS + SEND_MAIL_URL,
                        parameters, headParameters);
                new RPCBuriedPointSender().afterSend();

                CommonsHttpResult commonsHttpResult1 = gson.fromJson(mailResult, CommonsHttpResult.class);
                if ("000000".equals(commonsHttpResult1.getErrorCode())) {
                    commonsHttpResult.setErrorCode("000000");
                    commonsHttpResult.setMessage("Create account success");
                } else {
                    commonsHttpResult.setErrorCode("99999");
                    commonsHttpResult.setMessage("Send mail failed");
                }
            }
        } catch (BusinessException e) {
            commonsHttpResult.setErrorCode("99999");
            commonsHttpResult.setMessage("Create account failed");
        } catch (URISyntaxException e) {
            commonsHttpResult.setErrorCode("99999");
            commonsHttpResult.setMessage("Create account failed");
        } catch (IOException e) {
            commonsHttpResult.setErrorCode("99999");
            commonsHttpResult.setMessage("Create account failed");
        }

        return gson.toJson(commonsHttpResult);
    }
}
