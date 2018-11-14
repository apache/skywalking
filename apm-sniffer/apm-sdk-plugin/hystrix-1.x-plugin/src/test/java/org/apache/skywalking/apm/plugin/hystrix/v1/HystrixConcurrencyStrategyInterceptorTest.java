package org.apache.skywalking.apm.plugin.hystrix.v1;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HystrixConcurrencyStrategyInterceptorTest {

    private HystrixConcurrencyStrategyInterceptor hystrixConcurrencyStrategyInterceptor;

    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() {
        hystrixConcurrencyStrategyInterceptor = new HystrixConcurrencyStrategyInterceptor();
        enhancedInstance = new EnhancedInstance() {

            private SWHystrixPluginsWrapperCache cache;

            @Override
            public Object getSkyWalkingDynamicField() {
                return cache;
            }

            @Override public void setSkyWalkingDynamicField(Object cache) {
                this.cache = (SWHystrixPluginsWrapperCache) cache;
            }
        };
        HystrixPlugins.reset();
    }

    @Test
    public void testSWHystrixConcurrencyStrategyWrapperWillBeRegistered() throws Throwable {
        Object wrapperResult = getConcurrencyStrategyByInterceptor();
        assertTrue(wrapperResult instanceof SWHystrixConcurrencyStrategyWrapper);
        assertSame(wrapperResult, HystrixPlugins.getInstance().getConcurrencyStrategy());
    }


    @Test
    public void testInterceptorWithCustomHystrixConcurrencyStrategy() throws Throwable {
        Object wrapperResult = getConcurrencyStrategyByInterceptor();
        assertTrue(wrapperResult instanceof SWHystrixConcurrencyStrategyWrapper);
        assertSame(HystrixPlugins.getInstance().getConcurrencyStrategy(), wrapperResult);

        // register custom HystrixConcurrencyStrategy
        final HystrixConcurrencyStrategy delegate = getConcurrencyStrategyByInterceptor();
        HystrixConcurrencyStrategy customConcurrencyStrategy = new CustomConcurrencyStrategy(delegate);
        HystrixPlugins.reset();
        HystrixPlugins.getInstance().registerConcurrencyStrategy(customConcurrencyStrategy);

        // custom HystrixConcurrencyStrategy can be consumed
        wrapperResult = getConcurrencyStrategyByInterceptor();
        assertSame(customConcurrencyStrategy, wrapperResult);
        assertSame(HystrixPlugins.getInstance().getConcurrencyStrategy(), wrapperResult);
    }

    private HystrixConcurrencyStrategy getConcurrencyStrategyByInterceptor() throws Throwable {
        return (HystrixConcurrencyStrategy) hystrixConcurrencyStrategyInterceptor.afterMethod(enhancedInstance, null, null, null, HystrixPlugins.getInstance().getConcurrencyStrategy());
    }

    static class CustomConcurrencyStrategy extends HystrixConcurrencyStrategy {

        private HystrixConcurrencyStrategy delegate;

        public CustomConcurrencyStrategy(HystrixConcurrencyStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> Callable<T> wrapCallable(Callable<T> callable) {
            return delegate.wrapCallable(callable);
        }
    }
}