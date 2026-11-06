package io.github.vincenzofiore.rootology.spaces;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A person, known by their login (stored in lower case), whatever provider they sign in with. */
@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String login;
    private Instant createdAt;
    private Instant firstLoginAt;

    protected AppUser() {
    }

    AppUser(String login, Instant createdAt) {
        this.login = login;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getLogin() {
        return login;
    }

    /** False for a person added to a space who has not signed in yet. */
    boolean hasSignedIn() {
        return firstLoginAt != null;
    }

    void markFirstLogin(Instant now) {
        this.firstLoginAt = now;
    }
}
