package com.ai.cloud.skywalking.analysis.categorize2chain.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.config.Config;

public class SpanNodeProcessChain {
    private static Logger logger = LoggerFactory.getLogger(SpanNodeProcessChain.class.getName());
    private static Map<String, SpanNodeProcessFilter> filterMap;
    private static Object lock = new Object();

    private SpanNodeProcessChain() {
        //Non
    }

    private static void initFilterMap(Map<String, SpanNodeProcessFilter> filterMap) {
        Properties properties = new Properties();

        try {
            properties.load(SpanNodeProcessChain.class.getResourceAsStream("/viewpointfilter.conf"));
        } catch (IOException e) {
            logger.error("Failed to find config file[viewpointfilter.conf]", e);
            System.exit(-1);
        }

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String[] filters = ((String) entry.getValue()).split("->");
            String[] types = ((String) entry.getKey()).split(",");

            SpanNodeProcessFilter currentFilter = null;
            for (int i = filters.length - 1; i >= 0; i--) {
                try {
                    Class<?> filterClass = Class.forName(Config.Filter.FILTER_PACKAGE_NAME + "." + filters[i]);
                    SpanNodeProcessFilter tmpSpanNodeFilter = (SpanNodeProcessFilter) filterClass.newInstance();
                    tmpSpanNodeFilter.setNextProcessChain(currentFilter);
                    currentFilter = tmpSpanNodeFilter;
                } catch (ClassNotFoundException e) {
                    logger.error("Filed to find class[" + Config.Filter.FILTER_PACKAGE_NAME + "." + filters[i] + "]", e);
                    System.exit(-1);
                } catch (InstantiationException e) {
                    logger.error("Can not instance class[" + Config.Filter.FILTER_PACKAGE_NAME + "." + filters[i] + "]", e);
                    System.exit(-1);
                } catch (IllegalAccessException e) {
                    logger.error("Can not access class[" + Config.Filter.FILTER_PACKAGE_NAME + "." + filters[i] + "]", e);
                    System.exit(-1);
                } catch (ClassCastException e) {
                    logger.error("Class [" + Config.Filter.FILTER_PACKAGE_NAME + "." + filters[i] + "] is not subclass of " +
                            "com.ai.cloud.skywalking.analysis.filter.SpanNodeProcessFilter", e);
                    System.exit(-1);
                }
            }

            for (String type : types) {
                filterMap.put(type, currentFilter);
            }

        }
    }

    public static SpanNodeProcessFilter getProcessChainByCallType(String callType) {
        if (filterMap == null) {
            synchronized (lock) {
                if (filterMap == null) {
                    filterMap = new HashMap<String, SpanNodeProcessFilter>();
                    initFilterMap(filterMap);
                }
            }
        }

        if (filterMap.containsKey(callType)) {
            return filterMap.get(callType);
        }

        return filterMap.get("default");
    }

}
