package com.a.eye.skywalking.collector.distributed

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator

class WorkResultConsumer extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  def receive = {
    case _: DistributedPubSubMediator.SubscribeAck =>
    case WorkResult(workId, result) =>
      log.info("Consumed result: {}", result)
  }

}