package com.ai.cloud.skywalking.web.controller;

import com.ai.cloud.skywalking.web.dto.LoginUserInfo;
import com.ai.cloud.skywalking.web.common.BaseController;
import com.ai.cloud.skywalking.web.dao.inter.IAuthFileMaintainDao;
import com.ai.cloud.skywalking.web.dao.inter.ISystemConfigMaintainDao;
import com.ai.cloud.skywalking.web.util.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Created by xin on 16-3-29.
 */
@RequestMapping("/usr/applications/authfile")
@Controller
public class AuthFileController extends BaseController {

    private Logger logger = LogManager.getLogger(AuthFileController.class);

    @Autowired
    private ISystemConfigMaintainDao systemConfigMaintainDao;

    @Autowired
    private IAuthFileMaintainDao authFileMaintainDao;

    @RequestMapping("/todownload/{applicationCode}")
    public String toDownloadFile(@PathVariable("applicationCode") String applicationCode, HttpServletRequest request) {
        request.setAttribute("applicationId", applicationCode);
        return "usr/authfile/download";
    }

    @RequestMapping("/download/{applicationCode}")
    public void exportApplicationAuthFile(HttpServletRequest request, HttpServletResponse response,
                                          @PathVariable("applicationCode") String applicationCode, String authType) throws Exception {
        if (StringUtil.isBlank(applicationCode)) {
            return;
        }
        LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
        String filepath = loginUserInfo.getUserName() + "-" + applicationCode + ".jar";
        response.reset();
        response.setContentType("application/octet-stream");
        String fileName = URLDecoder.decode(filepath, "utf-8");
        java.net.URLEncoder.encode(fileName, "utf-8");
        response.addHeader("Content-Disposition",
                "attachment;" + "filename=\"" + URLEncoder.encode(fileName, "utf-8") + "\"");

        Properties properties = authFileMaintainDao.queryAuthKeysToProperties(authType);
        String propertyValue;
        for (Map.Entry<Object, Object> value : properties.entrySet()) {
            propertyValue = String.valueOf(value.getValue());
            if (propertyValue.startsWith("#")) {
                logger.info("{}", propertyValue.substring(1));
                value.setValue(systemConfigMaintainDao.querySystemConfigByKey(propertyValue.substring(1)).getConfValue());
                continue;
            }

            if (propertyValue.startsWith("$")) {
                if (request.getParameter((propertyValue.substring(1))) != null) {
                    value.setValue(request.getParameter(propertyValue.substring(1)));
                }
            }
        }
        properties.setProperty("skywalking.user_id", loginUserInfo.getUid());
        properties.setProperty("skywalking.application_code", applicationCode);

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
        } catch (Exception e) {
            logger.error("Failed to download the auth file.", e);
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
}
