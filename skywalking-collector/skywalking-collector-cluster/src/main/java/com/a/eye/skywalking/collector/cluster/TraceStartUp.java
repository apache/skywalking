package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.collector.cluster.consumer.TraceConsumerApp;
import com.a.eye.skywalking.collector.cluster.producer.TraceProducerApp;

public class TraceStartUp {

  public static void main(String[] args) {
    // starting 2 frontend nodes and 3 backend nodes
    TraceProducerApp.main(new String[0]);
    TraceProducerApp.main(new String[0]);
    TraceConsumerApp.main(new String[] { "2551" });
    TraceConsumerApp.main(new String[] { "2552" });
    TraceConsumerApp.main(new String[0]);
  }
}
