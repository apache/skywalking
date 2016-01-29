package com.ai.cloud.skywalking.alarm.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Encryption {
    private static Logger logger = LogManager.getLogger(DBConnectUtil.class);

    private MD5Encryption() {
    }

    public static String getEncryption(String originString) {
        String result = null;
        if (originString != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte bytes[] = md.digest(originString.getBytes());
                for (int i = 0; i < bytes.length; i++) {
                    String str = Integer.toHexString(bytes[i] & 0xFF);
                    if (str.length() == 1) {
                        str += "F";
                    }
                    result += str;
                }
            } catch (NoSuchAlgorithmException e) {
                logger.error("No such algorithmException.", e);
                return originString;
            }
        }
        return result.toUpperCase();
    }
}