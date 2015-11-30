package com.ai.cloud.skywalking.example.mail.controller;

import com.ai.cloud.skywalking.example.mail.util.SendeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

@Controller
@RequestMapping("/mail")
public class MaillController {

    private Logger logger = LogManager.getLogger(MaillController.class);

    @RequestMapping("/send")
    protected void sendMail(HttpServletRequest req, HttpServletResponse response) {
        String recipientAccount = req.getParameter("recipientAccount");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        OutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            if (recipientAccount == null || recipientAccount.length() <= 0) {
                outputStream.write("{\"errorCode\":\"99999\",\"message\":\"Recipient Account can not be null\"}".getBytes());
                outputStream.flush();
                return;
            }
            boolean result = SendeUtil.sendeMail(recipientAccount);
            if (result) {
                outputStream.write("{\"errorCode\":\"000000\",\"message\":\"Send mail success\"}".getBytes());
            } else {
                outputStream.write("{\"errorCode\":\"99999\",\"message\":\"Send mail failed\"}".getBytes());
            }
            outputStream.flush();
        } catch (Exception e) {
            logger.error("Send mail failed", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("Failed to close output stream", e);
                }
            }
        }
    }
}
