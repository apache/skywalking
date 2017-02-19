package com.a.eye.skywalking.collector.distributed

case class Work(workId: String, job: Any)

case class WorkResult(workId: String, result: Any)