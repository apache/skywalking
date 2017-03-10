package com.a.eye.skywalking.api.logging;

import java.io.PrintStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;

/**
 * Created by wusheng on 2017/2/28.
 */
public class STDOutWriterTest {
    private static PrintStream outRef;
    private static PrintStream errRef;

    @BeforeClass
    public static void initAndHoldOut(){
        outRef = System.out;
        errRef = System.err;
    }

    @Test
    public void testWrite(){
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setOut(mockStream);

        STDOutWriter writer = new STDOutWriter();
        writer.write("hello");

        Mockito.verify(mockStream,times(1)).println(anyString());
    }

    @Test
    public void testWriteError(){
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setErr(mockStream);

        STDOutWriter writer = new STDOutWriter();
        writer.writeError("hello");

        Mockito.verify(mockStream,times(1)).println(anyString());
    }

    @AfterClass
    public static void reset(){
        System.setOut(outRef);
        System.setErr(errRef);
    }
}
