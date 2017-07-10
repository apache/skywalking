package org.skywalking.apm.collector.core.util;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.skywalking.apm.collector.core.framework.Define;
import org.skywalking.apm.collector.core.framework.DefinitionFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class DefinitionLoader<D> implements Iterable<D> {

    private final Logger logger = LoggerFactory.getLogger(DefinitionLoader.class);

    private final Class<D> definition;
    private final DefinitionFile definitionFile;

    protected DefinitionLoader(Class<D> svc, DefinitionFile definitionFile) {
        this.definition = Objects.requireNonNull(svc, "definition interface cannot be null");
        this.definitionFile = definitionFile;
    }

    public static <D> DefinitionLoader<D> load(Class<D> definition, DefinitionFile definitionFile) {
        return new DefinitionLoader(definition, definitionFile);
    }

    @Override public final Iterator<D> iterator() {
        logger.info("load definition file: {}", definitionFile.get());
        Properties properties = new Properties();
        Map<String, String> definitionList = new LinkedHashMap<>();
        try {
            Enumeration<URL> urlEnumeration = this.getClass().getClassLoader().getResources(definitionFile.get());
            while (urlEnumeration.hasMoreElements()) {
                URL definitionFileURL = urlEnumeration.nextElement();
                logger.info("definition file url: {}", definitionFileURL.getPath());
                properties.load(new FileReader(definitionFileURL.getPath()));

                Enumeration defineItem = properties.propertyNames();
                while (defineItem.hasMoreElements()) {
                    String key = (String)defineItem.nextElement();
                    String fullNameClass = properties.getProperty(key);
                    definitionList.put(key, fullNameClass);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Iterator<Map.Entry<String, String>> moduleDefineIterator = definitionList.entrySet().iterator();

        return new Iterator<D>() {
            @Override public boolean hasNext() {
                return moduleDefineIterator.hasNext();
            }

            @Override public D next() {
                Map.Entry<String, String> moduleDefineEntry = moduleDefineIterator.next();
                String definitionName = moduleDefineEntry.getKey();
                String definitionClass = moduleDefineEntry.getValue();
                logger.info("key: {}, definitionClass: {}", definitionName, definitionClass);
                try {
                    Class c = Class.forName(definitionClass);
                    Define define = (Define)c.newInstance();
                    define.setName(definitionName);
                    return (D)define;
                } catch (Exception e) {
                }
                return null;
            }
        };
    }
}
