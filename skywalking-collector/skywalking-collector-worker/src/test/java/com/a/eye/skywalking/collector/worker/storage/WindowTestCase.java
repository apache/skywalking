package com.a.eye.skywalking.collector.worker.storage;

import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class WindowTestCase {

    @Test
    public void switchPointer() throws NoSuchFieldException, IllegalAccessException {
        Impl impl = new Impl();

        Field pointerField = impl.getClass().getSuperclass().getDeclaredField("pointer");
        pointerField.setAccessible(true);
        WindowData<DataImpl> pointer = (WindowData<DataImpl>)pointerField.get(impl);

        Field windowDataAField = impl.getClass().getSuperclass().getDeclaredField("windowDataA");
        windowDataAField.setAccessible(true);
        WindowData<DataImpl> windowDataA = (WindowData<DataImpl>)windowDataAField.get(impl);

        Field windowDataBField = impl.getClass().getSuperclass().getDeclaredField("windowDataB");
        windowDataBField.setAccessible(true);
        WindowData<DataImpl> windowDataB = (WindowData<DataImpl>)windowDataBField.get(impl);

        Assert.assertEquals(false, windowDataA.isHolding());
        WindowData<DataImpl> current = impl.getCurrentAndHold();
        Assert.assertEquals(current, windowDataA);
        Assert.assertEquals(true, windowDataA.isHolding());

        WindowData<DataImpl> last = impl.getLast();
        Assert.assertEquals(last, windowDataB);

        Assert.assertEquals(pointer, windowDataA);
        impl.switchPointer();
        pointer = (WindowData<DataImpl>)pointerField.get(impl);
        Assert.assertEquals(pointer, windowDataB);

        current = impl.getCurrentAndHold();
        Assert.assertEquals(current, windowDataB);
        Assert.assertEquals(true, windowDataB.isHolding());

        last = impl.getLast();
        Assert.assertEquals(last, windowDataA);

        impl.switchPointer();
        pointer = (WindowData<DataImpl>)pointerField.get(impl);
        Assert.assertEquals(pointer, windowDataA);
    }

    class Impl extends Window<DataImpl> {

    }

    class DataImpl implements Data {
        @Override public String getId() {
            return null;
        }

        @Override public void merge(Map<String, ?> dbData) {

        }
    }
}
