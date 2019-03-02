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

import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author ye-erpeng
 */
public class ElasticSearchUtil {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUtil.class);


    public static String[] getEsIndexByDate(String tableName, Long startTime, Long endTime) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        try {
            Date starts = formatter.parse(startTime.toString().substring(0, 8));
            Date ends = formatter.parse(endTime.toString().substring(0, 8));
            long length = (ends.getTime() - starts.getTime()) / (24 * 60 * 60 * 1000);
            if (length < 1) {
                length = 0;
            }
            String[] rs = new String[(int)length + 1];
            for (int j = 0; j < rs.length; j++) {
                rs[j] = tableName + "_" + formatter.format(DateUtils.addDays(starts, j));
            }
            return rs;
        } catch (Exception e) {
            logger.error("get indexes by date range error, error message: {}", e.getMessage());
        }
        return null;
    }
}
