package ie.itlimerick.summit.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebConfig {

    /**
     * Adds ETags to public read endpoints. Phones polling the agenda or announcements get a cheap
     * "304 Not Modified" when nothing changed, which keeps event-day traffic light.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> bean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        bean.addUrlPatterns("/api/v1/events/*", "/api/v1/sessions/*", "/api/v1/speakers/*", "/api/v1/teams/*");
        bean.setName("etagFilter");
        return bean;
    }
}
