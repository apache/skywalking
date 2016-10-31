package test.a.eye.cloud.bytebuddy;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

public class MethodInterceptor{
	@RuntimeType
	public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper){
		try {
			return method.getName() + ":intercept_" + zuper.call();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
