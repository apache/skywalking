package org.apache.skywalking.apm.plugin.hbase.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author zhangbin
 * @email 675953827@qq.com
 * @date 2019/4/26 22:14
 */
public class HBaseAdminInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.hadoop.hbase.client.HBaseAdmin";

    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.hbase.HBaseAdminInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("tableExists").or(named("listTables")).or(named("listTableNames")).or(named("getTableDescriptor"))
                                .or(named("createTable")).or(named("deleteTable")).or(named("modifyTable")).or(named("truncateTable"))
                                .or(named("enableTable")).or(named("enableTableAsync")).or(named("enableTables")).or(named("disableTableAsync"))
                                .or(named("disableTable")).or(named("disableTables")).or(named("getAlterStatus")).or(named("addColumn"))
                                .or(named("deleteColumn")).or(named("modifyColumn")).or(named("compact")).or(named("majorCompact"))
                                .or(named("split")).or(named("getTableRegions")).or(named("snapshot")).or(named("restoreSnapshot"))
                                .or(named("cloneSnapshot")).or(named("listSnapshots")).or(named("deleteSnapshot"));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
