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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Setter
@Getter
public class H2StorageConfig extends ModuleConfig {
    private String driver = "org.h2.jdbcx.JdbcDataSource";
    private String url = "jdbc:h2:mem:skywalking-oap-db;DB_CLOSE_DELAY=-1";
    private String user = "";
    private String password = "";
    private int metadataQueryMaxSize = 5000;
    /**
     * Some entities, such as trace segment, include the logic column with multiple values. Some storage support this
     * kind of data structure, but H2 doesn't.
     *
     * In the H2, we use multiple physical columns to host the values, such as,
     *
     * Change column_a with values [1,2,3,4,5] to
     * <p>
     * column_a_0 = 1, column_a_1 = 2, column_a_2 = 3 , column_a_3 = 4, column_a_4 = 5
     * </p>
     *
     * This configuration controls the threshold about how many physical columns should to be added, also limit the max
     * values of this kind of column.
     *
     * SkyWalking don't create a new table for indexing, because it would amplify the size of data set to dozens time,
     * which is not practical in the production environment.
     *
     * @since 8.2.0
     */
    private int maxSizeOfArrayColumn = 20;
    /**
     * In a trace segment, it includes multiple spans with multiple tags. Different spans could have same tag keys, such
     * as multiple HTTP exit spans all have their own `http.method` tag.
     *
     * This configuration set the limitation of max num of values for the same tag key.
     *
     * @since 8.2.0
     */
    private int numOfSearchableValuesPerTag = 2;
}
