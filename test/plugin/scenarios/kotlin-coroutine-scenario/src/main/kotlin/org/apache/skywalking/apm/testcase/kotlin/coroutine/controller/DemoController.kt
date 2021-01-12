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

package org.apache.skywalking.apm.testcase.kotlin.coroutine.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.skywalking.apm.testcase.kotlin.coroutine.service.DemoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/case")
class DemoController {
    @Autowired
    private lateinit var service: DemoService

    @ResponseBody
    @RequestMapping("/healthCheck")
    fun healthCheck(): String {
        return "Success"
    }

    @ResponseBody
    @RequestMapping("/h2")
    fun h2ThreadSwitchedCase(): String {
        val threadId = Thread.currentThread().id

        runBlocking {
            // Run blocking with default context will not switch thread
            if (threadId != Thread.currentThread().id) {
                throw IllegalStateException("Prerequisite failed")
            }

            service.work()
        }

        runBlocking(Dispatchers.IO) {
            // Run blocking with IO dispatcher will switch to IO thread
            if (threadId == Thread.currentThread().id) {
                throw IllegalStateException("Prerequisite failed")
            }

            service.work()
        }
        return "Success"
    }
}