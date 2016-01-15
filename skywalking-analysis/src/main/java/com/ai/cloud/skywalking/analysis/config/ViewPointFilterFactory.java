package com.ai.cloud.skywalking.analysis.config;

import com.ai.cloud.skywalking.analysis.viewpoint.ViewPointFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class ViewPointFilterFactory {

    private static Logger logger = LoggerFactory.getLogger(ViewPointFilterFactory.class.getName());

    private static Map<String, ViewPointFilter> filterType;

    private static Object lock = new Object();

    private ViewPointFilterFactory() {
        //Non
    }

    private static void initFilterChain() {
        Properties properties = new Properties();
        try {
            properties.load(ViewPointFilterFactory.class.getResourceAsStream("/viewpointfilter.conf"));
        } catch (IOException e) {
            logger.error("Failed to found the conf file[viewpointfilter.conf]", e);
            System.exit(-1);
        }

        Set<Map.Entry<Object, Object>> entries = properties.entrySet();

        for (Map.Entry<Object, Object> entry : entries) {
            String types = (String) entry.getKey();
            String filters = (String) entry.getValue();

            String[] filterClasses = filters.split(">");
            ViewPointFilter filter = null;
            for (int i = filterClasses.length - 1; i >= 0; i--) {
                try {
                    Class filterClass = Class.forName(filterClasses[i]);
                    ViewPointFilter newFilter = (ViewPointFilter) filterClass.newInstance();
                    newFilter.setViewPointFilter(filter);
                    filter = newFilter;
                } catch (ClassNotFoundException e) {
                    logger.error("Failed to found class[" + filterClasses[i] + "].", e);
                    System.exit(-1);
                } catch (InstantiationException e) {
                    logger.error("Failed to instance class[" + filterClasses[i] + "].", e);
                    System.exit(-1);
                } catch (IllegalAccessException e) {
                    logger.error("Failed to access class[" + filterClasses[i] + "].", e);
                    System.exit(-1);
                }
            }


            String[] type = types.split(",");
            for (int i = 0; i < type.length; i++) {
                filterType.put(type[i], filter);
            }
        }
    }

    public static ViewPointFilter getFilter(String type) {
        if (filterType == null) {
            synchronized (lock) {
                if (filterType == null) {
                    filterType = new HashMap<String, ViewPointFilter>();
                    initFilterChain();
                }
            }
        }

        ViewPointFilter filter = filterType.get(type);
        if (filter == null) {
            throw new RuntimeException("Failed to found the filter[" + type + "]");
        }

        return filter;
    }

}
