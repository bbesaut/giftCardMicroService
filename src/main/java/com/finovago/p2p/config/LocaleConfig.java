package com.finovago.p2p.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

// This API has no i18n - every error message in GlobalExceptionHandler is a hardcoded English
// string, except Bean Validation's built-in messages (@Min/@Max, etc.), which Hibernate Validator
// resolves against the request's Locale. Without this bean, Spring Boot defaults to
// AcceptHeaderLocaleResolver, which falls back to the JVM/OS default locale whenever no
// Accept-Language header is sent - so on a non-English host, validation error messages silently
// come back translated (e.g. French) while every other error message stays in English. Pinning the
// resolver makes error messages locale-independent, matching the rest of the API's contract.
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
