package com.finovago.p2p.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLog {
    private static final Logger log = LoggerFactory.getLogger(StartupLog.class);

    public StartupLog(Environment env) {
        String[] profiles = env.getActiveProfiles();
        String activeProfile = profiles.length > 0 ? profiles[0] : "default";
        log.info("Application started with profile: {}", activeProfile);
    }
}
