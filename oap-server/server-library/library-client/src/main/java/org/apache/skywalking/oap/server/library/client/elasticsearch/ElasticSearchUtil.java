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

package org.apache.skywalking.oap.server.library.client.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ye-erpeng
 */
public enum ElasticSearchUtil {

    INSTANCE;
    @Setter@Getter private int recordDataTTL;

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUtil.class);

    public String[] getEsIndexByDate(String tableName, Long startTime, Long endTime) {
        DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd");
        DateTime currTime = new DateTime();
        try {
            if (endTime <= 0) {
                endTime = Long.valueOf(currTime.toString("yyyyMMdd"));
            }
            if (startTime <= 0) {
                startTime = Long.valueOf(currTime.plusMinutes(-recordDataTTL).toString("yyyyMMdd"));
            }
            DateTime starts = DateTime.parse(startTime.toString().substring(0, 8), format);
            DateTime ends = DateTime.parse(endTime.toString().substring(0, 8), format);
            int length = Days.daysBetween(starts, ends).getDays();
            if (length < 1) {
                length = 0;
            }
            String[] rs = new String[length + 1];
            for (int j = 0; j < rs.length; j++) {
                rs[j] = tableName + "_" + starts.plusDays(j).toString("yyyyMMdd");
            }
            return rs;
        } catch (Exception e) {
            logger.error("get indexes by date range error, error message: {}", e.getMessage());
        }
        return null;
    }

}
