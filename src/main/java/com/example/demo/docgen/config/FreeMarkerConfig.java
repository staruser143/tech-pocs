package com.example.demo.docgen.config;

import com.example.demo.docgen.service.CustomFreeMarkerTemplateLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FreeMarkerConfig {

    @Bean
    @Primary
    public freemarker.template.Configuration freemarkerConfiguration(CustomFreeMarkerTemplateLoader customLoader) {
        freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        cfg.setTemplateLoader(customLoader);
        cfg.setDefaultEncoding("UTF-8");
        return cfg;
    }
}
