package com.a.eye.skywalking.collector

import akka.actor.ActorSystem
import akka.actor.Props

object CollectorApplication {
  def main(args: Array[String]) {
    val _system = ActorSystem("MapReduceApplication")
    val master = _system.actorOf(Props[MasterActor], name = "master")

    master ! "Hello,I love Spark. "
    master ! "Hello,I love Hadoop. "
    master ! "Hi, I love Spark and Hadoop. "

    Thread.sleep(500)
    master ! new ResultData

    Thread.sleep(500)
    _system.terminate()
  }
}