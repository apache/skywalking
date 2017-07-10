package org.skywalking.apm.collector.core.module;

import java.io.FileNotFoundException;
import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigLoader;
import org.skywalking.apm.collector.core.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author pengys5
 */
public class ModuleConfigLoader implements ConfigLoader {

    private final Logger logger = LoggerFactory.getLogger(ModuleConfigLoader.class);

    @Override public void load() throws ModuleConfigLoaderException {
        Yaml yaml = new Yaml();
        ModuleInstaller installer = new ModuleInstaller();

        Map<String, Map> configurations = null;
        try {
            configurations = (Map<String, Map>)yaml.load(ResourceUtils.read("application.yml"));
        } catch (FileNotFoundException e) {
            throw new ModuleConfigLoaderException(e.getMessage(), e);
        }
        configurations.forEach((moduleName, moduleConfig) -> {
            logger.info("module name \"{}\" from application.yml", moduleName);
            try {
                installer.install(moduleName, moduleConfig);
            } catch (ModuleException e) {
                logger.error("module \"{}\" install failure", moduleName);
                logger.error(e.getMessage(), e);
            }
        });
    }
}
