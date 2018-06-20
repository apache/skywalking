package org.apache.skywalking.apm.webapp.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;

/**
 * A container of login information.
 * 
 * @author gaohongtao
 */
class ReaderAccount implements Account {

    private final static Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .setLenient().create();
    
    private String username;
    
    private String password;
    
    static ReaderAccount newReaderAccount(final BufferedReader accountReader) {
        return GSON.fromJson(accountReader, ReaderAccount.class);
    }
    
    public String userName() {
        return username;
    }
    
    public String password() {
        return password;
    }
}
