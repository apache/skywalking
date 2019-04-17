package org.apache.skywalking.oap.server.receiver.envoy.als;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class DependencyResourceTest {

    @Parameterized.Parameter
    public String resourceName;

    @Parameterized.Parameter(1)
    public ThrowableFunction function;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"deploy1", (ThrowableFunction) result -> result},
                {"pod1", (ThrowableFunction) result -> { throw new RuntimeException(); } },
                {"pod1", (ThrowableFunction) result -> { throw new ApiException(); } },
                {"pod1", (ThrowableFunction) result -> null},
                {"rs1", (ThrowableFunction) result -> {
                    result.setOwnerReferences(null);
                    return result;
                } },
                {"rs1", (ThrowableFunction) result -> {
                    V1OwnerReference reference1 = new V1OwnerReference();
                    reference1.setKind("StatefulSet");
                    reference1.setName("ss1");
                    result.setOwnerReferences(Collections.singletonList(reference1));
                    return result;
                } },
        });
    }

    @Test
    public void test() {
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName("pod1");
        V1OwnerReference reference = new V1OwnerReference();
        reference.setKind("ReplicaSet");
        reference.setName("rs1");
        meta.addOwnerReferencesItem(reference);
        DependencyResource dr = new DependencyResource(meta);
        DependencyResource drr =  dr.getOwnerResource("ReplicaSet", ownerReference -> {
            assertThat(ownerReference.getName(), is("rs1"));
            V1ObjectMeta result = new V1ObjectMeta();
            result.setName("rs1");
            V1OwnerReference reference1 = new V1OwnerReference();
            reference1.setKind("Deployment");
            reference1.setName("deploy1");
            result.addOwnerReferencesItem(reference1);
            return function.go(result);
        }).getOwnerResource("Deployment", ownerReference -> {
            assertThat(ownerReference.getName(), is("deploy1"));
            V1ObjectMeta result = new V1ObjectMeta();
            result.setName("deploy1");
            return result;
        });
        assertThat(drr.getMetadata().getName(), is(resourceName));
    }

    interface ThrowableFunction {
        V1ObjectMeta go(final V1ObjectMeta result) throws ApiException;
    }

}