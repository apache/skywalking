package com.a.eye.skywalking.search;

import org.junit.Before;
import org.junit.Test;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
 * Created by xin on 2016/11/1.
 */
public class TreeSetTest {

    private TreeSet<Long> treeSet = new TreeSet<Long>();

    @Before
    public void setup(){
        treeSet.add(9L);
        treeSet.add(3L);
        treeSet.add(13L);
        treeSet.add(15L);
        treeSet.add(1L);
        treeSet.add(11L);
        treeSet.add(5L);
        treeSet.add(7L);
    }

    @Test
    public void testGetElement(){
        assertEquals(new Long(5), treeSet.higher(4L));
    }

    @Test
    public void testRemoveElement(){
        assertEquals(new Long(1), treeSet.first());
        treeSet.pollFirst();
        assertEquals(new Long(3), treeSet.first());
    }
}
