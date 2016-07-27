package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.common.CallType;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class MethodInputAndOutParameterInterceptor {
    public static IBuriedPointType METHOD_INVOKE_BURIEDPOINT = new IBuriedPointType() {
        @Override
        public String getTypeName() {
            return "LOCAL";
        }

        @Override
        public CallType getCallType() {
            return CallType.SYNC;
        }
    };
    @RuntimeType
    public Object interceptor(@AllArguments Object[] allArgument, @Origin Method method, @Origin Class<?> clazz, @SuperCall Callable<?> zuper) throws Exception {



        Object ret = null;
        try {
            ret = zuper.call();
        } catch (Throwable e) {

            throw e;
        } finally {

        }

        return ret;
    }
}
