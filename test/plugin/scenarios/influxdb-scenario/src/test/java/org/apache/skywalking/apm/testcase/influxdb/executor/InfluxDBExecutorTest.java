package org.apache.skywalking.apm.testcase.influxdb.executor;

import org.influxdb.dto.Point;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * InfluxDBExecutorTest
 *
 * @author guhao
 * @since 2020/6/17
 */
public class InfluxDBExecutorTest {

  private String serverURL = "http://127.0.0.1:8086";

  @Test
  public void write(){
    InfluxDBExecutor executor = new InfluxDBExecutor(serverURL);
    // createDatabase
    String db = "skywalking";
    executor.createDatabase(db);
    // createRetentionPolicy
    String rp = "one_day";
    executor.createRetentionPolicyWithOneDay(db,rp);
    Point point = Point.measurement("heartbeat")
        .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .tag("host", "127.0.0.1")
        .addField("device_name", "sensor x")
        .build();
    // write
    executor.write(db,rp,point);
    // query
    executor.query(db,"SELECT * FROM heartbeat");
  }

}
