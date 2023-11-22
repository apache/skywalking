package org.apache.skywalking.oap.query.debug;

import lombok.Getter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class DebuggingQueryConfig extends ModuleConfig {
    /**
     * Include the list of keywords to filter configurations including secrets. Separate keywords by a comma.
     *
     * @since 9.7.0
     */
    private String keywords4MaskingSecretsOfConfig = "user,password,token,accessKey,secretKey,authentication";
}
