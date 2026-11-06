package io.github.vincenzofiore.rootology.operations;

import io.github.vincenzofiore.rootology.mail.MailService;
import io.github.vincenzofiore.rootology.mail.OutgoingMail;
import io.github.vincenzofiore.rootology.notifications.NotificationPreferences;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.github.vincenzofiore.rootology.resilience.CircuitBreakers;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalAlertsTest {

    private final CircuitBreakers circuits = new CircuitBreakers(new SimpleMeterRegistry());
    private final CircuitBreaker jev = circuits.create("Jev");
    private final RoleService roles = mock(RoleService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MailService mail = mock(MailService.class);

    @BeforeEach
    void twoAdminsOneWithAnAddress() {
        when(roles.holders(AppRole.ADMIN)).thenReturn(List.of("ada", "bo"));
        when(notifications.preferences("ada"))
                .thenReturn(new NotificationPreferences("ada@example.test", true, true, null, null));
        when(notifications.preferences("bo")).thenReturn(new NotificationPreferences(null, true, true, null, null));
    }

    private OperationalAlerts alerts(double diskThreshold) {
        return new OperationalAlerts(circuits, roles, notifications, mail, "/", diskThreshold);
    }

    private void open(CircuitBreaker breaker) {
        for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
            breaker.failure();
        }
    }

    @Test
    void anOpenCircuitIsEmailedOnceToTheAdminsWithAnAddress() {
        OperationalAlerts alerts = alerts(1.1);
        open(jev);

        alerts.checkCircuits();
        alerts.checkCircuits();

        ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mail, times(1)).sendLater(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("ada@example.test");
        assertThat(sent.getValue().subject()).isEqualTo("Rootology: Jev non risponde");
        assertThat(sent.getValue().text()).contains("passano a una persona");
    }

    @Test
    void aCircuitThatClosedAndOpensAgainIsANewEvent() {
        OperationalAlerts alerts = alerts(1.1);
        open(jev);
        alerts.checkCircuits();

        jev.success();
        alerts.checkCircuits();
        open(jev);
        alerts.checkCircuits();

        verify(mail, times(2)).sendLater(any());
    }

    @Test
    void closedCircuitsSendNothing() {
        alerts(1.1).checkCircuits();

        verify(mail, never()).sendLater(any());
    }

    @Test
    void aNearlyFullDiskIsEmailedOnce() {
        // Threshold 0: any disk counts as nearly full.
        OperationalAlerts alerts = alerts(0.0);

        alerts.checkDisk();
        alerts.checkDisk();

        ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mail, times(1)).sendLater(sent.capture());
        assertThat(sent.getValue().subject()).isEqualTo("Rootology: disco quasi pieno");
    }

    @Test
    void aDiskBelowTheThresholdSendsNothing() {
        alerts(1.1).checkDisk();

        verify(mail, never()).sendLater(any());
    }
}
