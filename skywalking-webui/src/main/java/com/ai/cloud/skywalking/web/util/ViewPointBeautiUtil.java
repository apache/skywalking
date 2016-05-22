package com.ai.cloud.skywalking.web.util;

/**
 * Created by xin on 16-4-19.
 */
public class ViewPointBeautiUtil {


    public static String beautifulViewPoint(String viewPoint, String searchKey) {
        String highLightViewPoint = viewPoint;
        if (viewPoint.length() > 100) {
            highLightViewPoint = ViewPointBeautiUtil.addViewPoint(viewPoint, searchKey);
        }
        return ViewPointBeautiUtil.highLightViewPoint(highLightViewPoint, searchKey);
    }

    private static String highLightViewPoint(String viewPoint, String searchKey) {
        int index = viewPoint.indexOf(searchKey);
        if (index == -1) {
            return viewPoint;
        }
        StringBuilder result = new StringBuilder();
        if (index > 0) {
            result = new StringBuilder(viewPoint.substring(0, index - 1));
        }

        result.append("<span class='highlight-viewpoint'>");
        result.append(searchKey);
        result.append("</span>");
        if (viewPoint.length() > index + searchKey.length() + 1) {
            result.append(viewPoint.substring(index + searchKey.length() + 1));
        }
        return result.toString();
    }

    private static String addViewPoint(String viewPoint, String searchKey) {
        StringBuilder result = new StringBuilder();
        int startSize = 0;
        int index = viewPoint.indexOf(searchKey);
        int suffixLength = 50;
        if (index > suffixLength) {
            result.append(viewPoint.substring(0, suffixLength));
            result.append("...");
            result.append(viewPoint.substring(index, index + searchKey.length()));
            startSize = index + searchKey.length();
        } else if (index == suffixLength) {
            result.append(viewPoint.substring(0, suffixLength + searchKey.length()));
            startSize = suffixLength + searchKey.length();
        } else {
            result.append(viewPoint.substring(0, suffixLength));
            startSize = suffixLength;
        }
        if (startSize < viewPoint.length() - 40) {
            result.append("...");
            result.append(viewPoint.substring(viewPoint.length() - 40, viewPoint.length() - 20));
            result.append("....");
        } else if (startSize == viewPoint.length() - 40) {
            result.append(viewPoint.substring(viewPoint.length() - 40, viewPoint.length() - 20));
            result.append("....");
        } else {
            if (viewPoint.length() - 10 >=  startSize) {
                result.append(viewPoint.substring(startSize, viewPoint.length() - 10));
            }
        }
        return result.toString();
    }

}
