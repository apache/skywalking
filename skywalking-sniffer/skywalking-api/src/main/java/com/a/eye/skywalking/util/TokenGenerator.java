package com.a.eye.skywalking.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created data xin on 2016/12/4.
 */
public class TokenGenerator {

    public static long generate(String originData) {
        long value = 0;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(originData.getBytes());
            byte[] data = messageDigest.digest();
            //
            for (int i = 0; i < data.length; i++) {
                value = (value << 8) + (data[i] & 0xff);
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return value;
    }
}
