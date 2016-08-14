package test.a.eye.cloud.list;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by xin on 16-7-2.
 */
public class ArrayListTest {
    private List<String> data = new ArrayList<>();

    @Before
    public void initData() {
        data.add("AAAA");
        data.add("AAAAB");
        data.add("AAAAB");
        data.add("AAAAB");
    }

    @Test
    public void testPop() {
        data.remove(data.size() - 1);
        assertEquals(data.size(), 3);
    }

    @Test
    public void testPush() {
        data.add(data.size(), "BBBBB");
        assertEquals(data.get(data.size() - 1), "BBBBB");
    }
}
