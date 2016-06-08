package test.ai.cloud.matcher;

/**
 * Created by xin on 16-6-8.
 */
public class TestMatcherClass {

    public void set() {
        System.out.println("set()");
    }

    public void seta(String a) {
        set(a);
    }

    private void set(String a) {
        System.out.println("set(String a)");
    }

    public void get(String a) {
        System.out.println("get(String a)");
    }

    public void find() {
        System.out.println("find()");
    }

}
