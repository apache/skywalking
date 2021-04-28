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

package test.apache.skywalking.apm.testcase.mybatis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import test.apache.skywalking.apm.testcase.mybatis.service.DemoService;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    @Value("${mysql.servers}")
    private String address;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoService demoService;

    @RequestMapping("/mybatis-case")
    @ResponseBody
    public String mybatisCase() {
        demoService.doBiz();
        return "Success";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        try {
            jdbcTemplate.execute("create database if not exists test default charset = utf8");
            jdbcTemplate.execute("" + "CREATE TABLE IF NOT EXISTS `test`.`table_demo` (\n" + "  `id` bigint(20) NOT NULL AUTO_INCREMENT,\n" + "  `name` varchar(60),\n" + "  PRIMARY KEY (`id`)\n" + ") ENGINE=InnoDB");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return "success";
    }

}

