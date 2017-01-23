package com.a.eye.skywalking.web.util;

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


    public static final char VAL_SPLIT_CHAR = '.';

    public static Map<String, String> SPAN_TYPE_MAP = new HashMap<String, String>() {
        {
            put("M", "JAVA");
            put("J", "JDBC");
            put("W", "WEB");
            put("D", "DUBBO");
            put("L", "LOCAL");
            put("U", "UNKNOWN");
            put("MO", "Motan");
            put("OT", "OpenTracing");
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

    public static int MAX_SEARCH_SPAN_SIZE = 10000;

    public static int MAX_SHOW_SPAN_SIZE = 200;

    public static int MAX_ANALYSIS_RESULT_PAGE_SIZE = 10;
}
