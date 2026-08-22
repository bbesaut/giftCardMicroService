package com.finovago.p2p.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test initializer using a MailHog TestContainer - a real SMTP server that captures
 * messages instead of delivering them, exposing an HTTP API to read what was "sent". Lets
 * password-reset integration tests exercise the actual JavaMailSender/SMTP code path used by
 * SmtpEmailSender (active in dev/test) instead of mocking it. Prod uses a different sender
 * entirely (BrevoApiEmailSender, HTTP-based) since Render blocks outbound SMTP.
 *
 * Runs ONLY with: mvn test -P integration-tests
 */
public class MailHogTestcontainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;

    private static final GenericContainer<?> mailhog;

    static {
        try {
            mailhog = new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
                    .withExposedPorts(SMTP_PORT, HTTP_PORT);

            mailhog.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    mailhog.stop();
                } catch (Exception e) {
                    System.err.println("Failed to stop MailHog container: " + e.getMessage());
                }
            }));

            System.out.println("MailHog TestContainer started - web UI: http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(HTTP_PORT));
        } catch (Exception e) {
            throw new RuntimeException("Docker required for integration tests (MailHog container)", e);
        }
    }

    /** Base URL of MailHog's HTTP API, for tests asserting an email was received (GET /api/v2/messages). */
    public static String httpApiBaseUrl() {
        return "http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(HTTP_PORT);
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                "spring.mail.host=" + mailhog.getHost(),
                "spring.mail.port=" + mailhog.getMappedPort(SMTP_PORT)
        ).applyTo(applicationContext.getEnvironment());
    }
}
