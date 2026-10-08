package com.oracul.app.runs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RewriteWebConfig {

    @Bean
    FilterRegistrationBean<RewriteRunLookupFilter> rewriteRunLookupFilter(GenerationRunRepository runs) {
        FilterRegistrationBean<RewriteRunLookupFilter> reg = new FilterRegistrationBean<>(new RewriteRunLookupFilter(runs));
        reg.addUrlPatterns("/api/runs/*");
        reg.setOrder(Integer.MIN_VALUE + 30);
        return reg;
    }
}
