/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.finagle

import com.twitter.finagle.context.Contexts
import com.twitter.io.Buf
import com.twitter.util.{Return, Try}
import org.apache.skywalking.apm.agent.core.context.ContextCarrier

class SWContextCarrier() {
  private var operationName: String = ""
  private var carrier: ContextCarrier = null;

  def setOperationName(op: String): Unit = {
    operationName = op
  }

  def setContextCarrier(carrier: ContextCarrier): Unit = this.carrier = carrier;

  def getOperationName: String = operationName

  def getCarrier(): ContextCarrier = carrier;
}

object SWContextCarrier extends Contexts.broadcast.Key[SWContextCarrier]("org.apache.skywalking.apm.plugin.finagle.SWContextCarrier") {

  def of(carrier: ContextCarrier): SWContextCarrier = {
    val sWContextCarrier = new SWContextCarrier()
    sWContextCarrier.setContextCarrier(carrier)
    sWContextCarrier
  }

  override def marshal(context: SWContextCarrier): Buf = {
    CodecUtils.encode(context)
  }

  override def tryUnmarshal(buf: Buf): Try[SWContextCarrier] = {
    val context = CodecUtils.decode(buf)
    Return(context)
  }
}
