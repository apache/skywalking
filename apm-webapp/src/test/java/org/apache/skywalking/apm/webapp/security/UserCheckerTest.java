package org.apache.skywalking.apm.webapp.security;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserCheckerTest {

    @Test
    public void assertCheckSuccess() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("888888");
        checker.getUser().put("admin", user);
        assertTrue(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "888888";
            }
        }));
    }

    @Test
    public void assertCheckFail() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("123456");
        checker.getUser().put("guest", user);
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "888888";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "guest";
            }

            @Override public String password() {
                return "888888";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "123456";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "";
            }

            @Override public String password() {
                return "123456";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "";
            }
        }));
    }
}