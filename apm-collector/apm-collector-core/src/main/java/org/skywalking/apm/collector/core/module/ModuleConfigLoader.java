package org.skywalking.apm.collector.core.module;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigLoader;
import org.skywalking.apm.collector.core.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author pengys5
 */
public class ModuleConfigLoader implements ConfigLoader<Map<String, Map>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleConfigLoader.class);

    @Override public Map<String, Map> load() throws ModuleConfigLoaderException {
        Yaml yaml = new Yaml();
        try {
            try {
                FileReader applicationFileReader = ResourceUtils.read("application.yml");
                return (Map<String, Map>)yaml.load(applicationFileReader);
            } catch (FileNotFoundException e) {
                logger.info("Could not found application.yml file, use default");
                return (Map<String, Map>)yaml.load(ResourceUtils.read("application-default.yml"));
            }
        } catch (FileNotFoundException e) {
            throw new ModuleConfigLoaderException(e.getMessage(), e);
        }
    }
}
