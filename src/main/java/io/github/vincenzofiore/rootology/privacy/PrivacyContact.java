package io.github.vincenzofiore.rootology.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The controller named on the privacy page (`rootology.privacy`), set per deployment. */
@ConfigurationProperties("rootology.privacy")
public record PrivacyContact(String controller, String contact) {

    /** True when both are set; the page says so when they are not. */
    public boolean configured() {
        return controller != null && !controller.isBlank() && contact != null && !contact.isBlank();
    }
}
