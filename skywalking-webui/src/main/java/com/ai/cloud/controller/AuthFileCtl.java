package com.ai.cloud.controller;

import com.ai.cloud.service.inter.IAuthFileSer;
import com.ai.cloud.service.inter.ISystemConfigSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

@Controller
public class AuthFileCtl {

    private static Logger logger = LogManager.getLogger(AlarmRuleCtl.class);

    @Autowired
    private IAuthFileSer authFileSer;

    @Autowired
    private ISystemConfigSer systemConfigSer;


    @RequestMapping("/exportAuth/{appCode}")
    public void exportApplicationAuthFile(HttpServletRequest request, HttpServletResponse response,
                                          @PathVariable("appCode") String appCode, String authType) throws Exception {
        HttpSession session = request.getSession();
        String uid = (String) session.getAttribute("uid");
        String userName = (String) session.getAttribute("userName");
        JSONObject reJson = new JSONObject();

        if (StringUtil.isBlank(uid)) {
            reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
            reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
            return;
        }

        String filepath = userName + "-" + appCode + ".jar";
        response.reset();
        response.setContentType("application/octet-stream");
        String fileName = URLDecoder.decode(filepath, "utf-8");
        java.net.URLEncoder.encode(fileName, "utf-8");
        response.addHeader("Content-Disposition",
                "attachment;" + "filename=\"" + URLEncoder.encode(fileName, "utf-8") + "\"");

        Properties properties = authFileSer.queryAuthFile(authType);
        String propertyValue;
        for (Map.Entry<Object, Object> value : properties.entrySet()) {
            propertyValue = String.valueOf(value.getValue());
            if (propertyValue.startsWith("#")) {
                value.setValue(systemConfigSer.querySystemConfigByKey(propertyValue.substring(1)).getConfValue());
                continue;
            }

            if (propertyValue.startsWith("$")) {
                if (request.getParameter((propertyValue.substring(1))) != null) {
                    value.setValue(request.getParameter(propertyValue.substring(1)));
                }
            }
        }
        properties.setProperty("skywalking.user_id", uid);
        properties.setProperty("skywalking.application_code", appCode);

        File file = new File(request.getServletContext().getRealPath("/") + File.separator + "download" + File.separator + fileName);
        file.delete();
        // 生成JarFile
        JarOutputStream stream = new JarOutputStream(new FileOutputStream(file));
        JarEntry entry = new JarEntry("sky-walking.auth");
        stream.putNextEntry(entry);
        properties.store(stream, "");
        stream.flush();
        stream.close();
        FileInputStream inputStream = new FileInputStream(file);

        BufferedOutputStream output = null;
        BufferedInputStream input = null;
        OutputStream os = null;
        try {
            os = response.getOutputStream();
            byte[] bytes = new byte[1024];
            while ((inputStream.read(bytes)) != -1) {
                os.write(bytes);
            }
            //properties.store(os, "test");
        } catch (Exception e) {
            logger.error("导出 {} 应用制空权文件异常", appCode);
            e.printStackTrace();
        } finally {
            os.flush();
            os.close();
            inputStream.close();
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
        }
        return;
    }

    @RequestMapping("/confAuthInfo/{appCode}")
    public String confAuthInfo(HttpServletRequest request, HttpServletResponse response,
                               @PathVariable("appCode") String appCode) throws Exception {
        HttpSession session = request.getSession();
        String uid = (String) session.getAttribute("uid");

        JSONObject reJson = new JSONObject();

        if (StringUtil.isBlank(uid)) {
            reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
            reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
            return reJson.toJSONString();
        }

        return reJson.toJSONString();
    }

    @RequestMapping("/download/{appCode}")
    public String toDownloadAuthFile(@PathVariable("appCode") String appCode, ModelMap root) throws Exception {
        root.addAttribute("appCode", appCode);
        return "auth-file/authFile";
    }
}
