package app.rootology.operations;

import app.rootology.mail.MailService;
import app.rootology.mail.OutgoingMail;
import app.rootology.notifications.NotificationService;
import app.rootology.resilience.CircuitBreaker;
import app.rootology.resilience.CircuitBreakers;
import app.rootology.roles.AppRole;
import app.rootology.roles.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the circuits and the disk every {@code rootology.operations.check-every} and emails the admins, in
 * Italian, once per event: when a circuit opens (again only after it has closed), and when the disk's use
 * reaches {@code disk-threshold} (again only after it has gone below). In memory, so a restart may repeat one.
 */
@Component
class OperationalAlerts {

    private static final Logger log = LoggerFactory.getLogger(OperationalAlerts.class);

    private final CircuitBreakers circuits;
    private final RoleService roles;
    private final NotificationService notifications;
    private final MailService mail;
    private final Path diskPath;
    private final double diskThreshold;

    private final Set<String> openCircuits = new HashSet<>();
    private boolean diskAlerted;

    OperationalAlerts(CircuitBreakers circuits, RoleService roles, NotificationService notifications, MailService mail,
                      @Value("${rootology.operations.disk-path}") String diskPath,
                      @Value("${rootology.operations.disk-threshold}") double diskThreshold) {
        this.circuits = circuits;
        this.roles = roles;
        this.notifications = notifications;
        this.mail = mail;
        this.diskPath = Path.of(diskPath);
        this.diskThreshold = diskThreshold;
    }

    @Scheduled(fixedDelayString = "${rootology.operations.check-every}", initialDelayString = "${rootology.operations.check-every}")
    synchronized void check() {
        checkCircuits();
        checkDisk();
    }

    void checkCircuits() {
        for (CircuitBreaker breaker : circuits.all()) {
            String service = breaker.name();
            switch (breaker.state()) {
                case OPEN -> {
                    if (openCircuits.add(service)) {
                        alert("Rootology: " + service + " non risponde",
                                service + " non ha risposto più volte di seguito ed è stato escluso per qualche minuto. "
                                        + "L’app continua a funzionare con le sue alternative: " + fallbackOf(service));
                    }
                }
                case CLOSED -> openCircuits.remove(service);
                case HALF_OPEN -> {
                    // Still the same event until a call succeeds.
                }
            }
        }
    }

    void checkDisk() {
        double used;
        try {
            FileStore store = Files.getFileStore(diskPath);
            used = 1.0 - (double) store.getUsableSpace() / store.getTotalSpace();
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the disk usage of {}: {}", diskPath, e.getClass().getSimpleName());
            return;
        }
        if (used < diskThreshold) {
            diskAlerted = false;
        } else if (!diskAlerted) {
            diskAlerted = true;
            long percent = Math.round(used * 100);
            alert("Rootology: disco quasi pieno", "Il disco del server è pieno al " + percent
                    + "%: liberare spazio prima che Postgres o i backup smettano di funzionare.");
        }
    }

    private void alert(String subject, String text) {
        List<String> addresses = roles.holders(AppRole.ADMIN).stream()
                .map(admin -> notifications.preferences(admin).email())
                .filter(Objects::nonNull).toList();
        log.warn("Operational alert \"{}\" to {} admin(s)", subject, addresses.size());
        for (String to : addresses) {
            mail.sendLater(new OutgoingMail(to, subject, text, "<p>" + text + "</p>"));
        }
    }

    private static String fallbackOf(String service) {
        return switch (service) {
            case "Claude" -> "nessuna nuova domanda, e le risposte già decise sono frasi semplici con i valori.";
            case "Jev" -> "le domande passano a una persona dello spazio.";
            case "MET Norway" -> "si usa la previsione salvata, fino a 24 ore, poi niente meteo.";
            case "GeoNames" -> "la ricerca dei luoghi nell’orto non è disponibile.";
            case "Telegram" -> "le notifiche arrivano solo via email e nell’app.";
            case "Telegram updates" -> "collegare Telegram non funziona; i collegamenti già fatti continuano a ricevere.";
            default -> "controllare i log.";
        };
    }
}
