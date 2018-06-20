package org.apache.skywalking.apm.webapp.security;

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ReaderAccountTest {

    @Test
    public void assertNewReaderAccount() {
        Account account = ReaderAccount.newReaderAccount(new BufferedReader(new StringReader("{\"username\": \"admin\", \"password\":\"888888\"}")));
        assertThat(account.userName(), is("admin"));
        assertThat(account.password(), is("888888"));
    }
    
}