package com.a.eye.skywalking.api.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by xin on 16-6-24.
 */
public class ThrowableFormatter {
    public static String format(Throwable e) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        e.printStackTrace(new java.io.PrintWriter(buf, true));
        String expMessage = buf.toString();
        try {
            buf.close();
        } catch (IOException e1) {
            System.err.println("Failed to close throwable stack stream.");
            e.printStackTrace();
        }

        return expMessage;
    }
}
