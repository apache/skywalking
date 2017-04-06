package com.a.eye.skywalking.collector.worker.tools;

/**
 * @author pengys5
 */
public class UrlTools {

    private static final String HttpUrlHead = "http://";
    private static final String HttpsUrlHead = "https://";
    private static final String MotanUrlHead = "motan://";

    public static String parse(String url, String component) {
        if ("Tomcat".equals(component)) {
            return parseTomcat(url);
        } else if ("Motan".equals(component)) {
            return parseMotan(url);
        }
        return null;
    }

    private static String parseTomcat(String url) {
        if (url.startsWith(HttpUrlHead)) {
            String suffix = url.substring(7, url.length());
            String[] urlSplit = suffix.split("/");
            return HttpUrlHead + urlSplit[0];
        } else if (url.startsWith(HttpsUrlHead)) {
            String suffix = url.substring(8, url.length());
            String[] urlSplit = suffix.split("/");
            return HttpsUrlHead + urlSplit[0];
        } else if (url.contains(":")) {
            return url.split("/")[0];
        } else {
            return url;
        }
    }

    private static String parseMotan(String url) {
        if (url.startsWith(MotanUrlHead)) {
            String suffix = url.substring(8, url.length());
            String[] urlSplit = suffix.split("/");
            return MotanUrlHead + urlSplit[0];
        } else {
            return url;
        }
    }
}
