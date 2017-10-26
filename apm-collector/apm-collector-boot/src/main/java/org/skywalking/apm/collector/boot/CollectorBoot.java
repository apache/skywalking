/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.boot;

import com.google.protobuf.InvalidProtocolBufferException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;

public class CollectorBoot {

    public static void main(String[] args) throws InvalidProtocolBufferException, H2ClientException {
        H2Client client = new H2Client("jdbc:h2:~/h2", "sa", "");
        client.initialize();
        try (ResultSet rs = client.executeQuery("select * from segment", null)) {
            while (rs.next()) {
                byte[] dataBinary = rs.getBytes("data_binary");
                try {
                    parse(dataBinary);
                } catch (InvalidProtocolBufferException e) {
                }
            }
        } catch (SQLException | H2ClientException e) {
        }
    }

    public static void parse(byte[] dataBinary) throws InvalidProtocolBufferException {
        TraceSegmentObject segmentObject = TraceSegmentObject.parseFrom(dataBinary);
        for (TraceSegmentReference reference : segmentObject.getRefsList()) {
            StringBuilder builder = new StringBuilder();
            builder.append("entry: ").append(reference.getEntryApplicationInstanceId())
                .append(" ,parent: ").append(reference.getParentApplicationInstanceId())
                .append(" ,network: ").append(reference.getNetworkAddressId());
            System.out.println(segmentObject.getApplicationId());
            System.out.println(builder.toString());
        }
    }
}
