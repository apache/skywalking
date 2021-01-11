package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ZabbixConfigs {

    public static List<ZabbixConfig> loadConfigs(String path, List<String> fileNames) throws ModuleStartException {
        if (CollectionUtils.isEmpty(fileNames)) {
            return Collections.emptyList();
        }

        File[] configs;
        try {
            configs = ResourceUtils.getPathFiles(path, fileNames.toArray(new String[fileNames.size()]));
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Load zabbix configs failed", e);
        }

        return Arrays.stream(configs).filter(File::isFile)
            .map(f -> {
                try (Reader r = new FileReader(f)) {
                    return new Yaml().loadAs(r, ZabbixConfig.class);
                } catch (IOException e) {
                    log.warn("Reading file {} failed", f, e);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
