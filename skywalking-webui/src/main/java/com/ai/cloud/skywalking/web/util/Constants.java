package com.ai.cloud.skywalking.web.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 16-3-25.
 */
public class Constants {
    public static final String SESSION_LOGIN_INFO_KEY = "LOGIN_USER_INFO";

    public static class USR {
        public static final String ROLE_TYPE_USER = "user";
        public static final String STR_VAL_A = "A";
    }

    public static final String STATUS_CODE_9 = "9";


    public static final String TABLE_NAME_CHAIN = "sw-call-chain";
    public static final char VAL_SPLIT_CHAR = '.';
    public static final String RPC_END_FLAG = "-S";

    public static Map<String, String> SPAN_TYPE_MAP = new HashMap<String, String>() {
        {
            put("M", "JAVA");
            put("J", "JDBC");
            put("W", "WEB");
            put("D", "DUBBO");
            put("U", "UNKNOWN");
        }
    };

    public static final String SPAN_TYPE_U = "U";


    public static Map<String, String> STATUS_CODE_MAP = new HashMap<String, String>() {
        {
            put("0", "OK");
            put("1", "FAIL");
            put("9", "MISSING");
        }
    };
}
