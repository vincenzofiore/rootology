package io.github.vincenzofiore.rootology.spaces;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A shared space: a garden or a farm that one or more people work on. */
@Entity
@Table(name = "space")
class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Instant createdAt;

    protected Space() {
    }

    Space(String name, Instant createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
