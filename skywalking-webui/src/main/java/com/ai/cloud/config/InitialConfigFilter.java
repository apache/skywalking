package com.ai.cloud.config;

import com.ai.cloud.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.*;
import java.io.IOException;
import java.util.Properties;

public class InitialConfigFilter implements Filter {

    private Logger logger = LogManager.getLogger(InitialConfigFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Properties properties = new Properties();
        try {
            properties.load(InitialConfigFilter.class.getResourceAsStream("/config.properties"));
            ConfigInitializer.initialize(properties, Constants.class);
        } catch (IllegalAccessException e) {
            logger.error("Failed to init config.", e);
            System.exit(-1);
        } catch (IOException e) {
            logger.error("Failed to init config.", e);
            System.exit(-1);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
