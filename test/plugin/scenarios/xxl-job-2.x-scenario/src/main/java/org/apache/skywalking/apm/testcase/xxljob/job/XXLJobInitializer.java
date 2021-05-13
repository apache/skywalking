/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.xxljob.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;

@Slf4j
@Component
public class XXLJobInitializer implements InitializingBean {

    @Value("${xxl.mysql.address}")
    private String mysqlAddress;
    @Value("${xxl.mysql.root_password}")
    private String mysqlRootPassword;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializer xxl-job");

        initDatabase();
    }

    private void initDatabase() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");

        String url = String.format("jdbc:mysql://%s", mysqlAddress);
        try (Connection conn = DriverManager.getConnection(url, "root", mysqlRootPassword)) {
            ClassPathResource classPathResource = new ClassPathResource("tables_xxl_job.sql");
            ScriptUtils.executeSqlScript(conn, classPathResource);
        }
    }
}
