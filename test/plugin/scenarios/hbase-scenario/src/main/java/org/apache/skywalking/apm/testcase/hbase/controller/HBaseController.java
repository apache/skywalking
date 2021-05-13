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

package org.apache.skywalking.apm.testcase.hbase.controller;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class HBaseController {

    @Value("${hbase.servers:localhost}")
    private String address;

    private Table table;
    private final static Logger LOGGER = LoggerFactory.getLogger(HBaseController.class);

    @PostConstruct
    public void init() {
        Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", address);
        config.set("hbase.zookeeper.property.clientPort", "2181");
        config.set("hbase.client.ipc.pool.type", "RoundRobin");
        config.set("hbase.client.ipc.pool.size", "5");
        try {
            Admin admin = ConnectionFactory.createConnection(config).getAdmin();
            if (!admin.tableExists(TableName.valueOf("test_table"))) {
                HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("test_table"));
                HColumnDescriptor columnDescriptor = new HColumnDescriptor("family1");
                tableDescriptor.addFamily(columnDescriptor);
                admin.createTable(tableDescriptor);
            }
            table = admin.getConnection().getTable(TableName.valueOf("test_table"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/hbase-case")
    @ResponseBody
    public String hbaseCase() {
        try {
            Put put = new Put("rowkey1".getBytes());
            put.addColumn("family1".getBytes(), "qualifier1".getBytes(), "value1".getBytes());
            table.put(put);
            Scan s = new Scan();
            s.setFilter(new PrefixFilter("rowkey".getBytes()));
            s.setCaching(100);
            ResultScanner results = table.getScanner(s);
            for (Result result : results) {
                if (result != null && !result.isEmpty()) {
                    for (Cell cell : result.rawCells()) {
                        String family = Bytes.toString(CellUtil.cloneFamily(cell));
                        String colName = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
                        String value = Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
                        LOGGER.debug("family: " + family + " colName:" + colName + " value:" + value);
                    }
                }
            }
            Result result = table.get(new Get("rowkey1".getBytes()));
            for (Cell cell : result.rawCells()) {
                String family = Bytes.toString(CellUtil.cloneFamily(cell));
                String colName = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
                String value = Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
                LOGGER.debug("family: " + family + " colName:" + colName + " value:" + value);
            }
            table.delete(new Delete("rowkey1".getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Success";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "healthCheck";
    }
}
