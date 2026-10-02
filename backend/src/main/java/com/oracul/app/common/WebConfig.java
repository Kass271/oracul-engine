package com.oracul.app.common;

import com.oracul.app.session.SessionFilter;
import com.oracul.app.session.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConfig {

    @Bean
    FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        FilterRegistrationBean<RequestLogFilter> reg = new FilterRegistrationBean<>(new RequestLogFilter());
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Integer.MIN_VALUE + 10);
        return reg;
    }

    @Bean
    FilterRegistrationBean<SessionFilter> sessionFilter(SessionService sessions,
        @Value("${oracul.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        FilterRegistrationBean<SessionFilter> reg = new FilterRegistrationBean<>(new SessionFilter(sessions, frontendBaseUrl));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Integer.MIN_VALUE + 20);
        return reg;
    }
}
