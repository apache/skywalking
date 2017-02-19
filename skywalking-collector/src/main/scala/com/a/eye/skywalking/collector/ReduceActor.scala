package com.a.eye.skywalking.collector

import scala.collection.JavaConversions._
import java.util
import akka.actor.Actor
import akka.actor.ActorRef

class ReduceActor(aggregateActor: ActorRef) extends Actor {
  override def receive: Receive = {
    case message: MapData =>
      aggregateActor ! reduce(message.dataList)
    case _ =>
  }

  def reduce(dataList: util.ArrayList[Word]): ReduceData = {
    var reducedMap = new util.HashMap[String, Integer]
    for (wc: Word <- dataList) {
      var word: String = wc.word
      if (reducedMap.containsKey(word)) {
        reducedMap.put(word, reducedMap.get(word) + 1)
      } else {
        reducedMap.put(word, 1)
      }
    }

    reducedMap.foreach(f => println("word: " + f._1 + ", count: " + f._2))

    return new ReduceData(reducedMap)
  }
}