package org.skywalking.apm.agent.core.plugin.utility;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.auxiliary.AuxiliaryType;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.pool.TypePool;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClassFileExtraction {

    private static final int CA = 0xCA, FE = 0xFE, BA = 0xBA, BE = 0xBE;

    public static Map<String, byte[]> of(Class<?>... type) throws IOException {
        Map<String, byte[]> result = new HashMap<String, byte[]>();
        for (Class<?> aType : type) {
            result.put(aType.getName(), extract(aType));
        }
        return result;
    }

    public static byte[] extract(Class<?> type, AsmVisitorWrapper asmVisitorWrapper) throws IOException {
        ClassReader classReader = new ClassReader(type.getName());
        ClassWriter classWriter = new ClassWriter(classReader, AsmVisitorWrapper.NO_FLAGS);
        classReader.accept(asmVisitorWrapper.wrap(new TypeDescription.ForLoadedType(type),
            classWriter,
            new IllegalContext(),
            TypePool.Empty.INSTANCE,
            new FieldList.Empty<FieldDescription.InDefinedShape>(),
            new MethodList.Empty<MethodDescription>(),
            AsmVisitorWrapper.NO_FLAGS,
            AsmVisitorWrapper.NO_FLAGS), AsmVisitorWrapper.NO_FLAGS);
        return classWriter.toByteArray();
    }

    public static byte[] extract(Class<?> type) throws IOException {
        return extract(type, new AsmVisitorWrapper.Compound());
    }

    @Test
    public void testClassFileExtraction() throws Exception {
        byte[] binaryFoo = extract(Foo.class);
        assertThat(binaryFoo.length > 4, is(true));
        assertThat(binaryFoo[0], is(new Integer(CA).byteValue()));
        assertThat(binaryFoo[1], is(new Integer(FE).byteValue()));
        assertThat(binaryFoo[2], is(new Integer(BA).byteValue()));
        assertThat(binaryFoo[3], is(new Integer(BE).byteValue()));
    }

    private static class Foo {
        /* empty */
    }

    private static class IllegalContext implements Implementation.Context {

        @Override
        public TypeDescription register(AuxiliaryType auxiliaryType) {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public FieldDescription.InDefinedShape cache(StackManipulation fieldValue, TypeDescription fieldType) {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public TypeDescription getInstrumentedType() {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public ClassFileVersion getClassFileVersion() {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public MethodDescription.InDefinedShape registerAccessorFor(
            Implementation.SpecialMethodInvocation specialMethodInvocation, AccessType accessType) {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public MethodDescription.InDefinedShape registerGetterFor(FieldDescription fieldDescription,
            AccessType accessType) {
            throw new AssertionError("Did not expect method call");
        }

        @Override
        public MethodDescription.InDefinedShape registerSetterFor(FieldDescription fieldDescription,
            AccessType accessType) {
            throw new AssertionError("Did not expect method call");
        }
    }
}
