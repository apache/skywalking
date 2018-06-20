package org.apache.skywalking.apm.webapp.security;

import com.netflix.zuul.context.RequestContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.UndeclaredThrowableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LoginTest {

    private LoginFilter loginFilter;

    @Mock
    private RequestContext ctx;

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;

    @Before
    public void setUp() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("admin");
        checker.getUser().put("admin", user);
        loginFilter = new LoginFilter(checker);
        when(ctx.getRequest()).thenReturn(request);
        when(ctx.getResponse()).thenReturn(response);
        RequestContext.testSetCurrentContext(ctx);
    }

    @Test
    public void assertSuccessLogin() throws IOException {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"username\": \"admin\", \"password\":\"admin\"}")));
        loginFilter.run();
        assertHeaderAndStatusCode();
        verify(ctx).setResponseBody("{\"status\":\"ok\",\"currentAuthority\":\"admin\"}");
    }
    
    @Test
    public void assertFailLogin() throws IOException {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"username\": \"admin\", \"password\":\"888888\"}")));
        loginFilter.run();
        assertHeaderAndStatusCode();
        verify(ctx).setResponseBody("{\"status\":\"error\",\"currentAuthority\":\"guest\"}");
    }

    @Test(expected = UndeclaredThrowableException.class)
    public void assertException() throws IOException {
        when(request.getReader()).thenThrow(new IOException());
        loginFilter.run();
    }
    
    private void assertHeaderAndStatusCode() {
        verify(ctx).setResponseStatusCode(HttpServletResponse.SC_OK);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
    }
}
