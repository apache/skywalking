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

package org.apache.skywalking.oap.server.library.util;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;

public class ConnectUtils {

    private ConnectUtils() {
    }

    public static List<Address> parse(String connectString) throws ConnectStringParseException {
        connectString = connectString == null ? "" : connectString.trim();
        connectString = connectString.startsWith(",") ? connectString.replace(",", "") : connectString;

        if (Strings.isNullOrEmpty(connectString)) {
            throw new ConnectStringParseException("ConnectString cannot be null or empty.");
        }

        List<Address> result = new ArrayList<>();

        String[] connects = connectString.split(",");
        for (String connect : connects) {
            if (Strings.isNullOrEmpty(connect)) {
                throw new ConnectStringParseException("Invalid connect string pattern.");
            }

            String[] hostAndPort = connect.split(":");
            if (hostAndPort.length != 2) {
                throw new ConnectStringParseException("Invalid connect string pattern.");
            }

            Address address = new Address();
            address.setHost(hostAndPort[0]);

            try {
                address.setPort(Integer.parseInt(hostAndPort[1]));
            } catch (NumberFormatException e) {
                throw new ConnectStringParseException("Invalid connect string pattern.");
            }
            result.add(address);
        }

        return result;
    }
}
