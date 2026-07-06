package com.scripty.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CacheControlFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/sw.js")) {
            res.setHeader("Cache-Control", "no-cache, must-revalidate");
        } else if (isAppPage(path)) {
            res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            res.setHeader("Pragma", "no-cache");
            res.setHeader("Expires", "0");
        }

        chain.doFilter(request, response);
    }

    private static boolean isAppPage(String path) {
        return !path.startsWith("/css/")
                && !path.startsWith("/js/")
                && !path.startsWith("/icons/")
                && !path.startsWith("/fonts/")
                && !"/favicon.ico".equals(path)
                && !"/manifest.json".equals(path)
                && !"/sw.js".equals(path)
                && !"/offline.html".equals(path)
                && !path.startsWith("/h2-console");
    }
}
