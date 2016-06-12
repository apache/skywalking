package test.ai.cloud.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.pool.TypePool;

public class SimulateMain2 {
	public static void main(String[] args) throws InstantiationException,
			IllegalAccessException {
		TypePool typePool = TypePool.Default.ofClassPath();

		new ByteBuddy()
				.rebase(typePool.describe("test.ai.cloud.bytebuddy.TestClass")
						.resolve(),
						ClassFileLocator.ForClassLoader.ofClassPath())
				.method(named("testA"))
				.intercept(MethodDelegation.to(new MethodInterceptor()))
				.method(named("testB"))
				.intercept(MethodDelegation.to(new MethodInterceptor()))
				.constructor(isConstructor())
				.intercept(
						MethodDelegation.to(new ConstructorInterceptor())
								.andThen(SuperMethodCall.INSTANCE))
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded();

		TestClass t = new TestClass("abc");
		System.out.println(t.testA("1"));
	}
}
