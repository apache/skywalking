package com.ai.cloud.skywalking.web.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-4-19.
 */
public class ViewPointBeautiUtil {


    public static String beautifulViewPoint(String viewPoint, String searchKey) {
        String highLightViewPoint = ViewPointBeautiUtil.addViewPoint(viewPoint, searchKey);
        return ViewPointBeautiUtil.highLightViewPoint(highLightViewPoint, searchKey);
    }

    private static String highLightViewPoint(String viewPoint, String searchKey) {
        int index = viewPoint.indexOf(searchKey);
        if (index == -1) {
            return viewPoint;
        }
        StringBuilder result = new StringBuilder(viewPoint.substring(0, index - 1));
        result.append("<span class='highlight-viewpoint'>");
        result.append(searchKey);
        result.append("</span>");
        result.append(viewPoint.substring(index + searchKey.length() + 1));
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
            result.append(viewPoint.substring(startSize, viewPoint.length() - 10));
        }
        return result.toString();
    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("tracing:jdbc:oracle:thin:@10.1.1.61:1521:OAPROD(aisse)preaparedStatement.executeQuery:select a.ACCOUNT_TYPE,a.PHONE_ACCOUNT,a.PHONE_NUMBER,a.ATTRIBUTE4 from AISSE_EMPLOYEE_MOBILE_INFO_V a where PHONE_NUMBER  is not null and NT_ACCOUNT = ? and ACCOUNT_TYPE not in ('统一充值','United Voucher'):preaparedStatement.executeQuery:select a.ACCOUNT_TYPE,a.PHONE_ACCOUNT,a.PHONE_NUMBER,a.ATTRIBUTE4 from AISSE_EMPLOYEE_MOBILE_INFO_V a where PHONE_NUMBER  is not null and NT_ACCOUNT = ? and ACCOUNT_TYPE not in ('统一充值','United Voucher')");
        list.add("tracing:jdbc:oracle:thin:@10.1.1.61:1521:OAPROD(aisse)preaparedStatement.executeQuery:select a.ACCOUNT_TYPE,a.PHONE_ACCOUNT,a.PHONE_NUMBER,a.ATTRIBUTE4 from AISSE_EMPLOYEE_MOBILE_INFO_V a where  lower(a.NT_ACCOUNT) = ?:preaparedStatement.executeQuery:select a.ACCOUNT_TYPE,a.PHONE_ACCOUNT,a.PHONE_NUMBER,a.ATTRIBUTE4 from AISSE_EMPLOYEE_MOBILE_INFO_V a where  lower(a.NT_ACCOUNT) = ?");
        list.add("dubbo://aisse-mobile-web/com.ai.aisse.core.rest.ExpenseInitApi.searchMembersinfo(String):");
        list.add("com.ai.aisse.core.dao.impl.QueryUserMessageDaoImpl.selectDemoList(java.lang.String):");
        list.add("com.ai.aisse.controller.common.CommonController.toAisseMobilePage(com.ai.net.xss.wrapper.XssRequestWrapper,org.apache.catalina.connector.ResponseFacade,org.springframework.validation.support.BindingAwareModelMap):");
        for (String string : list) {
            System.out.println(beautifulViewPoint(string, "ACCOUNT_TYPE"));
        }
    }
}
