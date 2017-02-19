package com.a.eye.skywalking.collector

import akka.actor.Actor
import scala.collection.JavaConversions._
import java.util

class AggregateActor extends Actor {

  var finalReducedMap = new util.HashMap[String, Integer]

  override def receive: Receive = {
    case message: ReduceData =>
      aggregateInMemoryReduce(message.reduceDataMap)
    case message: ResultData =>
      System.out.println(finalReducedMap.toString)
  }

  def aggregateInMemoryReduce(reducedList: util.HashMap[String, Integer]) = {
    var count: Integer = 0
    for (key <- reducedList.keySet) {
      if (finalReducedMap.containsKey(key)) {
        count = reducedList.get(key)
        count += finalReducedMap.get(key)
        finalReducedMap.put(key, count)
      } else {
        finalReducedMap.put(key, reducedList.get(key))
      }
    }
  }
}