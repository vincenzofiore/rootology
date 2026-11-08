package io.github.vincenzofiore.rootology;

import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Broken access control (#41, OWASP A01): a member of one space tries the ids of another space's gardens,
 * plots, plantings, proposals, invitations, history and escalations, and is refused every time; the other
 * space's data stays as it was.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CrossSpaceAccessTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeEnrichment enrichment;

    @Autowired
    InvitationService invitations;

    @Autowired
    RunHistory history;

    @Autowired
    GardenPipeline pipeline;

    String victim;
    String intruder;
    long victimSpace;
    long intruderSpace;
    long garden;
    long plot;
    long planting;

    @BeforeEach
    void twoSpaces() {
        String suffix = Long.toString(System.nanoTime(), 36);
        victim = "victim-" + suffix;
        intruder = "intruder-" + suffix;
        spaces.signedIn(victim);
        spaces.signedIn(intruder);
        victimSpace = spaces.spacesOf(victim).getFirst().id();
        intruderSpace = spaces.spacesOf(intruder).getFirst().id();
        garden = gardens.saveGarden(victimSpace, victim, null, "Victim garden", null, null, null).id();
        plot = gardens.savePlot(victimSpace, victim, garden, null, "A", PlotKind.GROUND, Soil.LOAMY, null).id();
        planting = gardens.plant(victimSpace, victim, plot, "tomato", PlantingMethod.SOWN, LocalDate.of(2026, 4, 1), null)
                .id();
    }

    @Test
    void anotherSpacesIdsAreRefusedFromTheIntrudersOwnSpace() {
        // The intruder uses their own space, where they may change things, with the victim's ids.
        assertThatThrownBy(() -> gardens.plots(intruderSpace, intruder, garden)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deleteGarden(intruderSpace, intruder, garden)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.savePlot(intruderSpace, intruder, garden, plot, "Mine", PlotKind.GROUND, Soil.CLAY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deletePlot(intruderSpace, intruder, plot)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.plantings(intruderSpace, intruder, plot)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.endPlanting(intruderSpace, intruder, planting, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deletePlanting(intruderSpace, intruder, planting)).isInstanceOf(IllegalArgumentException.class);

        long proposal = enrichment.propose(victimSpace, victim, ProposalScope.SPACE,
                new Contribution.Alias("tomato", "pomodorino di casa", "it"), null).id();
        assertThatThrownBy(() -> enrichment.withdraw(intruderSpace, intruder, proposal)).isInstanceOf(IllegalArgumentException.class);
        assertThat(enrichment.source(intruderSpace, intruder, "proposal-" + proposal)).isEmpty();

        invitations.create(victimSpace, victim, Role.MEMBER, null);
        long invitation = invitations.pending(victimSpace, victim).getFirst().id();
        assertThatThrownBy(() -> invitations.revoke(intruderSpace, intruder, invitation)).isInstanceOf(RuntimeException.class);

        // Nothing of the victim's changed.
        assertThat(gardens.plantings(victimSpace, victim, plot)).singleElement()
                .satisfies(p -> assertThat(p.endedOn()).isNull());
        assertThat(enrichment.ofSpace(victimSpace, victim)).hasSize(1);
        assertThat(invitations.pending(victimSpace, victim)).hasSize(1);
    }

    @Test
    void aGardenOfAnotherSpaceIsUnknown() {
        // #56: the garden id from another space is never found, whichever space the person uses it with.
        long otherGarden = gardens.saveGarden(intruderSpace, intruder, null, "Intruder garden", null, null, null).id();

        assertThat(gardens.garden(victimSpace, victim, otherGarden)).isEmpty();
        assertThat(gardens.garden(intruderSpace, intruder, garden)).isEmpty();
        assertThatThrownBy(() -> gardens.garden(victimSpace, intruder, garden)).isInstanceOf(RuntimeException.class);
        // The questions of the victim's garden never show under a garden id of the intruder's space.
        assertThat(pipeline.pending(intruderSpace, garden, intruder)).isEmpty();
        assertThat(history.recent(intruderSpace, garden, intruder)).isEmpty();
    }

    @Test
    void anotherSpaceIsRefusedOutright() {
        assertThatThrownBy(() -> gardens.gardens(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> gardens.plots(victimSpace, intruder, garden)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> enrichment.ofSpace(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> invitations.pending(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> history.recent(victimSpace, garden, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> pipeline.pending(victimSpace, garden, intruder)).isInstanceOf(RuntimeException.class);
        assertThat(enrichment.source(victimSpace, intruder, "proposal-1")).isEmpty();
    }
}
