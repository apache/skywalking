package test.a.eye.cloud.matcher;

/**
 * Created by xin on 16-6-8.
 */
public class TestMatcherClass {

    public void set() {
        System.out.println("public set()");
    }

    public void seta(String a) {
        System.out.println("public seta(String a)");
        set(a);
    }

    private void set(String a) {
        System.out.println("private set(String a)");
    }

    public void get(String a) {
        System.out.println("public get(String a)");
    }

    public void find() {
        System.out.println("public find()");
    }

    @Override
    public String toString() {
        return "Call toString()";
    }

    @Override
    public boolean equals(Object obj) {
        System.out.println("equals(Object obj)");
        return true;
    }
}
