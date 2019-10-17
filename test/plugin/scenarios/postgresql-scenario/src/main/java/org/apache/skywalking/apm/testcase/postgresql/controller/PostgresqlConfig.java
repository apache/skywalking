package org.apache.skywalking.apm.testcase.postgresql.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostgresqlConfig {

    private static Logger logger = LogManager.getLogger(PostgresqlConfig.class);

    @Value("${POSTGRESQL_SERVER}")
    private String host;

    @Value("${POSTGRES_DB}")
    private String db;

    @Value("${POSTGRES_USER}")
    private String user;

    @Value("${POSTGRES_PASSWORD}")
    private String password;

    public String getUrl() {
        return "jdbc:postgresql://" + host + "/" + db;
    }

    public String getUserName() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
