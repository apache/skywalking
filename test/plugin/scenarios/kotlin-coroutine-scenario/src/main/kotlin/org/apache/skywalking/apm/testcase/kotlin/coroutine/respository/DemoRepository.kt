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

package org.apache.skywalking.apm.testcase.kotlin.coroutine.respository

import org.apache.commons.dbcp.BasicDataSource
import org.apache.skywalking.apm.testcase.kotlin.coroutine.util.use
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class DemoRepository {
    private val datasource: DataSource = BasicDataSource().apply {
        driverClassName = "org.h2.Driver"
        url = String.format("jdbc:h2:mem:demo")
        username = ""
        password = ""
    }

    init {
        datasource.connection.use {
            prepareStatement("CREATE TABLE PERSON(ID INT auto_increment primary key, NAME varchar(255))").execute()
            prepareStatement("INSERT INTO PERSON (NAME) VALUES (?)").apply {
                setString(1, "kanro")
                execute()
            }
        }
    }

    fun getPersonName(id: Int): String? {
        return datasource.connection.use {
            prepareStatement("SELECT * FROM PERSON WHERE ID = ?").run {
                setInt(1, id)
                val result = executeQuery()
                if (result.next()) {
                    result.getString("NAME")
                } else {
                    null
                }
            }
        }
    }
}