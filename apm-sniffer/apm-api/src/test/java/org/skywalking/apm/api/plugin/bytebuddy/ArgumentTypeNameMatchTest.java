package org.skywalking.apm.api.plugin.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

/**
 * @author wusheng
 */
public class ArgumentTypeNameMatchTest {
    @Test
    public void testMatches() throws IllegalAccessException {
        MethodDescription methodDescription = Mockito.mock(MethodDescription.class, Mockito.RETURNS_DEEP_STUBS);
        ParameterDescription parameterDescription = Mockito.mock(ParameterDescription.class, Mockito.RETURNS_DEEP_STUBS);
        when(methodDescription.getParameters().get(0)).thenReturn(parameterDescription);
        when(methodDescription.getParameters().size()).thenReturn(1);
        when(parameterDescription.getType().asErasure().getName()).thenReturn("org.skywalking.TestClass");

        ArgumentTypeNameMatch matcher = ((ArgumentTypeNameMatch) ArgumentTypeNameMatch.takesArgumentWithType(0, "org.skywalking.TestClass"));
        Assert.assertTrue(matcher.matches(methodDescription));

        ArgumentTypeNameMatch matcher2 = ((ArgumentTypeNameMatch) ArgumentTypeNameMatch.takesArgumentWithType(0, "org.skywalking.TestClass2"));
        Assert.assertFalse(matcher2.matches(methodDescription));
    }

    @Test
    public void testMatchesWithNoParameters() {
        MethodDescription methodDescription = Mockito.mock(MethodDescription.class, Mockito.RETURNS_DEEP_STUBS);
        ParameterDescription parameterDescription = Mockito.mock(ParameterDescription.class, Mockito.RETURNS_DEEP_STUBS);
        when(methodDescription.getParameters().size()).thenReturn(0);

        ArgumentTypeNameMatch matcher2 = ((ArgumentTypeNameMatch) ArgumentTypeNameMatch.takesArgumentWithType(0, "org.skywalking.TestClass"));
        Assert.assertFalse(matcher2.matches(methodDescription));
    }
}
