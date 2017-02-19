package com.a.eye.skywalking.collector

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef

class MasterActor extends Actor {
  val aggregateActor: ActorRef = context.actorOf(Props[AggregateActor], name = "aggregate")
  val reduceActor: ActorRef = context.actorOf(Props(new ReduceActor(aggregateActor)), name = "reduce")
  val mapActor: ActorRef = context.actorOf(Props(new MapActor(reduceActor)), name = "map")

  override def receive: Receive = {
    case message: String =>
      mapActor ! message
    case messge: ResultData =>
      aggregateActor ! messge
    case _ =>
  }
}