package org.apache.skywalking.apm.agent.core.util;

import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.apache.skywalking.apm.agent.core.conf.ConfigReadFailedException;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropertiesUtil {

    private static final ILog logger = LogManager.getLogger(PropertiesUtil.class);

    public static Class initConfigClass(Class clazz,Properties properties, String agentOptions, String envKeyPrefix) throws IllegalAccessException {
        ConfigInitializer.initialize(properties, clazz);
        PropertiesUtil.overrideConfigBySystemProp(envKeyPrefix, clazz);
        PropertiesUtil.overrideConfigByAgentOptions(agentOptions, clazz);
        return clazz;
    }

    public static Properties Properties(InputStreamReader configFileStream) throws IOException {
        Properties properties = new Properties();
        properties.load(configFileStream);
        for (String key : properties.stringPropertyNames()) {
            String value = (String) properties.get(key);
            //replace the key's value. properties.replace(key,value) in jdk8+
            properties.put(key, PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value, properties));
        }
        return properties;
    }

    /**
     * Load the specified config file
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    public static InputStreamReader loadConfigFile(String configPath) throws ConfigNotFoundException, ConfigReadFailedException {

        File configFile = new File(configPath);

        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("Config file found in {}.", configFile);
                return new InputStreamReader(new FileInputStream(configFile), "UTF-8");
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Failed to load " + configPath, e);
            } catch (UnsupportedEncodingException e) {
                throw new ConfigReadFailedException("Failed to load " + configPath, e);
            }
        }
        throw new ConfigNotFoundException("Failed to load " + configPath);
    }

    /**
     * Override the config by system properties. The property key must start with `skywalking`, the result should be as same
     * as in `agent.config`
     * <p>
     * such as: Property key of `agent.service_name` should be `skywalking.agent.service_name`
     */
    public static void overrideConfigBySystemProp(String envKeyPrefix, Class clazz) throws IllegalAccessException {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        Iterator<Map.Entry<Object, Object>> entryIterator = systemProperties.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Object, Object> prop = entryIterator.next();
            String key = prop.getKey().toString();
            if (key.startsWith(envKeyPrefix)) {
                String realKey = key.substring(envKeyPrefix.length());
                properties.put(realKey, prop.getValue());
            }
        }

        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, clazz);
        }
    }

    public static void overrideConfigByAgentOptions(String agentOptions, Class clazz) throws IllegalAccessException {
        Properties properties = new Properties();
        for (List<String> terms : parseAgentOptions(agentOptions)) {
            if (terms.size() != 2) {
                throw new IllegalArgumentException("[" + terms + "] is not a key-value pair.");
            }
            properties.put(terms.get(0), terms.get(1));
        }
        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, clazz);
        }
    }

    public static List<List<String>> parseAgentOptions(String agentOptions) {
        List<List<String>> options = new ArrayList<List<String>>();
        List<String> terms = new ArrayList<String>();
        boolean isInQuotes = false;
        StringBuilder currentTerm = new StringBuilder();
        for (char c : agentOptions.toCharArray()) {
            if (c == '\'' || c == '"') {
                isInQuotes = !isInQuotes;
            } else if (c == '=' && !isInQuotes) {   // key-value pair uses '=' as separator
                terms.add(currentTerm.toString());
                currentTerm = new StringBuilder();
            } else if (c == ',' && !isInQuotes) {   // multiple options use ',' as separator
                terms.add(currentTerm.toString());
                currentTerm = new StringBuilder();

                options.add(terms);
                terms = new ArrayList<String>();
            } else {
                currentTerm.append(c);
            }
        }
        // add the last term and option without separator
        terms.add(currentTerm.toString());
        options.add(terms);
        return options;
    }
}
