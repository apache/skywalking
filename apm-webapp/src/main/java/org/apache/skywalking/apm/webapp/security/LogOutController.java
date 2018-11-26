package org.apache.skywalking.apm.webapp.security;

import com.google.gson.Gson;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by qibaichao on 2018/11/23.
 */
@Controller
public class LogOutController {

    @RequestMapping(value = "/logoutRequest")
    @ResponseBody
    public String say(HttpServletRequest request, HttpServletResponse response) {
        //session失效
        request.getSession().invalidate();
        System.out.println("session Id " + request.getRequestedSessionId());
        String resStr = null;
        Gson gson = new Gson();
        try {
            resStr = gson.toJson(new ResponseData("ok", "admin", "http://172.16.1.61:8080/logout?service=http://contract.test.renrendai.com:8080"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resStr;
    }

    private static class ResponseData {
        private final String status;
        private final String currentAuthority;
        private final String casServerLoginUrl;

        ResponseData(final String status, final String currentAuthority, final String casServerLoginUrl) {
            this.status = status;
            this.currentAuthority = currentAuthority;
            this.casServerLoginUrl = casServerLoginUrl;
        }
    }
}
