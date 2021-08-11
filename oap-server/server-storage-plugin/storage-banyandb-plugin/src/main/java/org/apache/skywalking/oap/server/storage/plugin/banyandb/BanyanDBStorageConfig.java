package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class BanyanDBStorageConfig extends ModuleConfig {
    private String host = "127.0.0.1";
    private int port = 8080;
}
