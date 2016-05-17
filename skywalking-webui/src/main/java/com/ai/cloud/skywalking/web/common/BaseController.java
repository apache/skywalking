package com.ai.cloud.skywalking.web.common;

import com.ai.cloud.skywalking.web.exception.UserInvalidateException;
import com.ai.cloud.skywalking.web.exception.UserNotLoginException;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.dto.LoginUserInfo;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BaseController {
    @ModelAttribute
    public void initPath(HttpServletRequest request, HttpServletResponse response, ModelMap model) {
        String base = request.getContextPath();
        String fullPath = request.getScheme() + "://" + request.getServerName() +
                ":" + request.getServerPort() + base;

        LoginUserInfo loginUserInfo = (LoginUserInfo) request.getSession().getAttribute(Constants.SESSION_LOGIN_INFO_KEY);
        if (loginUserInfo != null) {
            model.addAttribute("loginUser", loginUserInfo);
        }
        model.addAttribute("_base", base);

    }


    protected LoginUserInfo fetchLoginUserInfoFromSession(HttpServletRequest request) {
        //Get login user Id
        LoginUserInfo loginUserInfo = (LoginUserInfo) request.getSession().
                getAttribute(Constants.SESSION_LOGIN_INFO_KEY);
        if (loginUserInfo == null) {
            throw new UserNotLoginException("Failed to find login user info");
        }

        if (loginUserInfo.getUid() == null) {
            throw new UserInvalidateException("Login user Id is null");
        }

        return loginUserInfo;
    }
}
