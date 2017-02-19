package com.a.eye.skywalking.collector.distributed

import scala.concurrent.duration._
import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout
import akka.cluster.singleton.{ ClusterSingletonProxySettings, ClusterSingletonProxy }

object Frontend {
  case object Ok
  case object NotOk
}

class Frontend extends Actor {
  import Frontend._
  import context.dispatcher
  val masterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"),
    name = "masterProxy")

  def receive = {
    case work =>
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work) map {
        case Master.Ack(_) => Ok
      } recover { case _ => NotOk } pipeTo sender()

  }

}