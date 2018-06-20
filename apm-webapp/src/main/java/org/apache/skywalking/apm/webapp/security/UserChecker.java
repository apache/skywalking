package org.apache.skywalking.apm.webapp.security;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A Checker to check username and password.
 * 
 * @author gaohongtao
 */
@Component
@ConfigurationProperties(prefix = "security")
public class UserChecker {
    
    private Map<String, User> user = new HashMap<>();

    public Map<String, User> getUser() {
        return user;
    }

    boolean check(Account account) {
        if (Strings.isNullOrEmpty(account.userName()) || Strings.isNullOrEmpty(account.password())) {
            return false;
        }
        if (!user.containsKey(account.userName())) {
            return false;
        }
        return user.get(account.userName()).password.equals(account.password());
    }
    
    public static class User {
        private String password;

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
