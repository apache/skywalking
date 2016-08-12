package test.com.ai.test;

public class TestObject {
    public static void staticPrintlnHelloWorld(String aa, TestParam param){
        System.out.println("Hello World" + aa);
    }

    public void printlnHelloWorld(TestParam... params){
        System.out.println("Hello World");
    }
}
