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

package org.apache.skywalking.apm.plugin.quartz;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.quartz.Calendar;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.Job;
import org.quartz.JobExecutionException;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.calendar.CronCalendar;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Date;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class QuartzJobExecutorInterceptorTest {

    private String testGroup = "testGroup";

    private String testJob = "testJob";

    private String cron = "0/5 * * * * ?";

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private QuartzJobExecutorInterceptor quartzJobExecutorInterceptor;

    @Before
    public void setUp() {
        quartzJobExecutorInterceptor = new QuartzJobExecutorInterceptor();
    }

    @Test
    public void assertSuccess() throws Throwable {

        JobExecutionContext jobExecutionContext = mockShardingContext();
        quartzJobExecutorInterceptor.beforeMethod(null, null, new Object[]{
                jobExecutionContext,
                1
        }, null, null);
        quartzJobExecutorInterceptor.afterMethod(null, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).getOperationName(), is(testGroup + "_" + testJob));
        assertThat(spans.get(0)
                .transform()
                .getTags(0)
                .getValue(), is(jobExecutionContext.toString()));
    }

    private JobExecutionContext mockShardingContext() throws Exception {

        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler scheduler = sf.getScheduler();
        JobDetail jobDetail = JobBuilder.newJob(TestJob.class).withIdentity(testJob, testGroup).build();
        OperableTrigger operableTrigger = new SimpleTriggerImpl();
        Calendar calendar = new CronCalendar(cron);
        TriggerFiredBundle triggerFiredBundle = new TriggerFiredBundle(jobDetail, operableTrigger, calendar, false, new Date(1594299528953L), new Date(1594299528953L), new Date(1594299528953L), new Date(1594299528953L));
        TestJob testJob = new TestJob();
        JobExecutionContextImpl jobExecutionContext = new JobExecutionContextImpl(scheduler, triggerFiredBundle, testJob);
        return jobExecutionContext;
    }

    public class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        }
    }
}
