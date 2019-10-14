/*
 *
 * Copyright (C) 2016-2017 HIIRI Inc.All Rights Reserved.
 *
 * ProjectName：apm
 *
 * Description：
 *
 * History：
 * Version    Author            Date              Operation
 * 1.0	      xuzs         2019/10/15 上午12:03	        Create
 */
package org.apache.skywalking.apm.plugin.jdbc.postgresql.define;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.plugin.jdbc.PSSetterDefinitionOfJDBCInstrumentation;

public class PgPreparedStatementSetterInstrumentation extends PgPreparedStatementInstrumentation {

    @Override
    public final InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new PSSetterDefinitionOfJDBCInstrumentation(false)
        };
    }

}
