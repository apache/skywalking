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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;

@Slf4j
public class MySQLTableInstaller extends JDBCTableInstaller {
    /**
     * The most raw bytes a MEDIUMTEXT column holds once a byte array is stored as Base64, four characters for
     * every three bytes: 16,777,215 characters, rounded down to whole groups.
     */
    private static final int MEDIUMTEXT_RAW_BYTES = 16_777_215 / 4 * 3;

    public MySQLTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    public void start() {
        /*
         * Override column because the default column names in core have syntax conflict with MySQL.
         */
        overrideColumnName("value", "value_");
        overrideColumnName("precision", "cal_precision");
        overrideColumnName("match", "match_num");
    }

    @Override
    public String getColumnDefinition(final ModelColumn column) {
        final var storageName = column.getColumnName().getStorageName();
        final var type = column.getType();
        if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " MEDIUMTEXT";
        } else if (String.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " MEDIUMTEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        } else if (JsonObject.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " MEDIUMTEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        } else if (byte[].class.equals(type)) {
            // the length of a byte array column is the most raw bytes it must hold; MEDIUMTEXT holds fewer than
            // its character count once they are Base64
            return storageName + (column.getLength() > MEDIUMTEXT_RAW_BYTES ? " LONGTEXT" : " MEDIUMTEXT");
        }
        return super.getColumnDefinition(column);
    }
}
