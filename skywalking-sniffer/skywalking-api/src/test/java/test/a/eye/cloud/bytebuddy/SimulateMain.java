package test.a.eye.cloud.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.pool.TypePool;

public class SimulateMain {
	public static void main(String[] args) throws NoSuchFieldException,
			SecurityException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		TypePool typePool = TypePool.Default.ofClassPath();
		System.out.println(typePool.describe("TestClass").isResolved());

		Class<?> newClazz = new ByteBuddy()
				.redefine(
						typePool.describe("TestClass")
								.resolve(),
						ClassFileLocator.ForClassLoader.ofClassPath())
				.name("TestClass$$Origin")
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded();

		TestClass t22 = (TestClass) (new ByteBuddy()
				.subclass(newClazz, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
				.method(isMethod())
				.intercept(MethodDelegation.to(new MethodInterceptor()))
				.constructor(isConstructor())
				.intercept(MethodDelegation.to(new ConstructorInterceptor()).andThen(SuperMethodCall.INSTANCE))
				.name("TestClass")
				.make()
				.load(ClassLoader.getSystemClassLoader(),
						ClassLoadingStrategy.Default.INJECTION).getLoaded()
				.newInstance());

		// System.out.println(t22.testA("1"));
		TestClass t = new TestClass("abc");
		System.out.println(t.testA("1"));
		
		t = new TestClass("abc");
		System.out.println(t.testA("1"));

//		TestClass t2 = null;
//		try {
//			t2 = (TestClass) Class.forName("TestClass")
//					.newInstance();
//		} catch (ClassNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		System.out.println(t2.testA("1"));
	}
}
