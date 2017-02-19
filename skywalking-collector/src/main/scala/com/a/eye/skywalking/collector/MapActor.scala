package com.a.eye.skywalking.collector

import java.util
import java.util.StringTokenizer
import akka.actor.Actor
import akka.actor.ActorRef

class MapActor(reduceActor: ActorRef) extends Actor {
  // don't count words include (a,is)  
  val STOP_WORDS_LIST = List("a", "is")

  override def receive: Receive = {
    case message: String =>
      reduceActor ! evaluateExpression(message)
    case _ =>
  }

  def evaluateExpression(line: String): MapData = {
    val dataList = new util.ArrayList[Word]
    val doLine = line.replaceAll("[,!?.]", " ")
    var parser: StringTokenizer = new StringTokenizer(doLine)

    val defaultCount: Integer = 1
    while (parser.hasMoreTokens()) {
      var word: String = parser.nextToken().toLowerCase()
      if (!STOP_WORDS_LIST.contains(word)) {
        dataList.add(new Word(word, defaultCount))
      }
    }

    for (i <- 0 to dataList.size() - 1) {
      val word = dataList.get(i)
      println(line + "   word:" + word.word + ", count: " + word.count)
    }

    return new MapData(dataList)
  }
} 