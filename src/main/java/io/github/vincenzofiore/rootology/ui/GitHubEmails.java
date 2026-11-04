package io.github.vincenzofiore.rootology.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The verified email addresses of the signed-in GitHub account (#25), read once at sign-in with the
 * `user:email` scope (https://docs.github.com/en/rest/users/emails). Empty when GitHub cannot be reached:
 * then only invitations bound to an email cannot be accepted.
 */
class GitHubEmails {

    static final String ATTRIBUTE = "verified_emails";

    private static final Logger log = LoggerFactory.getLogger(GitHubEmails.class);

    record Address(String email, boolean verified) {
    }

    private final RestClient restClient;

    GitHubEmails(String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(timeouts).baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28").build();
    }

    GitHubEmails(RestClient restClient) {
        this.restClient = restClient;
    }

    Set<String> verified(String accessToken) {
        try {
            List<Address> addresses = restClient.get().uri("/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<Address>>() { });
            return addresses == null ? Set.of() : addresses.stream().filter(Address::verified)
                    .map(a -> a.email().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            log.warn("Could not read the verified email addresses from GitHub: {}", e.getMessage());
            return Set.of();
        }
    }
}
