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

package org.apache.skywalking.apm.testcase.quartzscheduler.job;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.Trigger;
import org.quartz.JobBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.CronScheduleBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Map;

@Configuration
public class QuartzConfig {

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public Scheduler scheduler() throws SchedulerException, ParseException {
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();

        Map.Entry<JobDetail, Trigger> demoJobConfig = demoJobConfig();
        scheduler.scheduleJob(demoJobConfig.getKey(), demoJobConfig.getValue());

        Map.Entry<JobDetail, Trigger> exceptionJobConfig = exceptionJobConfig();
        scheduler.scheduleJob(exceptionJobConfig.getKey(), exceptionJobConfig.getValue());

        return scheduler;
    }

    private Map.Entry<JobDetail, Trigger> demoJobConfig() throws ParseException {
        JobDetail demoJobDetail = JobBuilder.newJob(DemoJob.class)
                .withIdentity("DemoJob", "DemoJobGroup")
                .usingJobData("param1", "test")
                .storeDurably()
                .build();

        Trigger demoJobTrigger = TriggerBuilder.newTrigger()
                .forJob(demoJobDetail)
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule("0/5 * * * * ?"))
                .build();

        return new AbstractMap.SimpleEntry(demoJobDetail, demoJobTrigger);
    }

    private Map.Entry<JobDetail, Trigger> exceptionJobConfig() throws ParseException {
        JobDetail exceptionJobDetail = JobBuilder.newJob(ExceptionJob.class)
                .withIdentity("ExceptionJob", "ExceptionJobGroup")
                .usingJobData("param1", "test")
                .storeDurably()
                .build();

        Trigger exceptionJobTrigger = TriggerBuilder.newTrigger()
                .forJob(exceptionJobDetail)
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule("0/5 * * * * ?"))
                .build();

        return new AbstractMap.SimpleEntry(exceptionJobDetail, exceptionJobTrigger);
    }
}