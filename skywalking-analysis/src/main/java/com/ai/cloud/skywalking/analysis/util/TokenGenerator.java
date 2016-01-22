package com.ai.cloud.skywalking.analysis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TokenGenerator {
    private static Logger logger = LoggerFactory.getLogger(TokenGenerator.class.getName());

    private TokenGenerator() {
        //Non
    }
    
    public static String generateCID(String originData) {
    	return "CID_" + generate(originData);
    }
    
    public static String generateNodeToken(String originData){
    	return "C_NID_" + generate(originData);
    }

    private static String generate(String originData) {
        StringBuilder result = new StringBuilder();
        if (originData != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte bytes[] = md.digest(originData.getBytes());
                for (int i = 0; i < bytes.length; i++) {
                    String str = Integer.toHexString(bytes[i] & 0xFF);
                    if (str.length() == 1) {
                        str += "F";
                    }
                    result.append(str);
                }
            } catch (NoSuchAlgorithmException e) {
                logger.error("Cannot found algorithm.", e);
                System.exit(-1);
            }
        }
        return result.toString().toUpperCase();
    }
}
