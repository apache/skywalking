package org.apache.skywalking.apm.testcase.influxdb.executor;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

/**
 * InfluxDBExecutor
 *
 * @author guhao
 * @since 2020/6/3
 */
public class InfluxDBExecutor implements AutoCloseable {

  private final InfluxDB influxDB;

  public InfluxDBExecutor(String serverURL){
    influxDB = InfluxDBFactory.connect(serverURL,"admin",null);
  }

  public Pong ping(){
    return influxDB.ping();
  }

  public QueryResult createDatabase(String databaseName){
    // Create a database...
    return influxDB.query(new Query("CREATE DATABASE " + databaseName, null));
  }

  public QueryResult createRetentionPolicyWithOneDay(String databaseName, String retentionPolicyName){
//        influxDB.setDatabase(databaseName);
    // ... and a retention policy, if necessary.
    return influxDB.query(new Query("CREATE RETENTION POLICY " + retentionPolicyName + " ON " + databaseName + " DURATION 1d REPLICATION 1 DEFAULT", databaseName));
  }

  public void write(String databaseName, String retentionPolicyName,Point point){
    // Write points to InfluxDB.
    influxDB.write(databaseName, retentionPolicyName, point);
  }

  public QueryResult query(String databaseName,String command){
    // Query your data using InfluxQL.
    return influxDB.query(new Query(command, databaseName));
  }

  @Override
  public void close() throws Exception {
    if (influxDB != null){
      // Close it if your application is terminating or you are not using it anymore.
      influxDB.close();
    }
  }
}
