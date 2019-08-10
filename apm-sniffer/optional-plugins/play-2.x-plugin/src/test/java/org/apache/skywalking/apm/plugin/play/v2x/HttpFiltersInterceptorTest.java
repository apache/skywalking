package org.apache.skywalking.apm.plugin.play.v2x;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import play.api.inject.BindingKey;
import play.api.inject.Injector;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Seq$;
import scala.reflect.ClassTag;

import java.util.Objects;

/**
 * @author AI
 * 2019-08-07
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class HttpFiltersInterceptorTest {

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private Object object = null;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Mock
    private MethodInterceptResult methodInterceptResult;

    private HttpFiltersInterceptor interceptor = new HttpFiltersInterceptor();
    private Injector injector = new Injector() {
        @Override
        public <T> T instanceOf(ClassTag<T> evidence$1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T instanceOf(Class<T> clazz) {
            return (T) new TracingFilter(null);
        }

        @Override
        public <T> T instanceOf(BindingKey<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public play.inject.Injector asJava() {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void testBindingInjector() {
        Object[] arguments = new Object[]{injector};
        interceptor.onConstruct(enhancedInstance, arguments);
        Assert.assertTrue(Objects.nonNull(enhancedInstance.getSkyWalkingDynamicField()));
        Assert.assertTrue(enhancedInstance.getSkyWalkingDynamicField() instanceof Injector);
    }

    @Test
    public void testReturningTracingFilter() throws Throwable {
        Seq ret = Seq$.MODULE$.empty();
        enhancedInstance.setSkyWalkingDynamicField(injector);
        Object result = interceptor.afterMethod(enhancedInstance, null, null, null, ret);
        Assert.assertTrue(Objects.nonNull(result));
        Seq filters = (Seq) result;
        Assert.assertTrue(filters.head() instanceof TracingFilter);
    }

}
