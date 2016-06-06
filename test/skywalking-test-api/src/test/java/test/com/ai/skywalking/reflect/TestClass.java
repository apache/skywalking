package test.com.ai.skywalking.reflect;

public class TestClass {

    private static TestSubClass testSubClass = new TestSubClass();

    static class TestSubClass {
        private static String[] testStringArray = new String[5];
    }
}
