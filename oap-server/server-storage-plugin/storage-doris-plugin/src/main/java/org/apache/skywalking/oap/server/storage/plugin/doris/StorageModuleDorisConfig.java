package org.apache.skywalking.oap.server.storage.plugin.doris;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Setter
@Getter
public class StorageModuleDorisConfig extends ModuleConfig {
    private String host;
    private int port;
    private String user;
    private String password;
    private String database;
}
