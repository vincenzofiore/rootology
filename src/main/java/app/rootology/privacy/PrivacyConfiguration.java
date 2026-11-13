package app.rootology.privacy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ Retention.class, PrivacyContact.class })
class PrivacyConfiguration {
}
