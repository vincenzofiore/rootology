package app.rootology.officialwarnings;

import app.rootology.resilience.CircuitBreaker;
import app.rootology.resilience.CircuitBreakers;
import app.rootology.resilience.ServiceUnavailableException;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

/**
 * Fetches MeteoAlarm's Italian feed and the CAP documents it links (#136), through one circuit breaker and with
 * short timeouts like every external call (#29): a warning not read is not worth a request that hangs. The feed
 * sends no {@code ETag} or {@code Last-Modified}, so every read is a plain GET of the whole document.
 */
@Component
class MeteoAlarmClient {

    /** The breaker's name, as the health check and the operational alerts show it. */
    static final String SERVICE = "meteoalarm";

    private static final String FEED = "/feeds/meteoalarm-legacy-atom-italy";

    private static final Logger log = LoggerFactory.getLogger(MeteoAlarmClient.class);

    private final RestClient restClient;
    private final URI baseUrl;
    private final CircuitBreaker breaker;

    @Autowired
    MeteoAlarmClient(@Value("${rootology.meteoalarm.base-url}") String baseUrl, CircuitBreakers circuits) {
        this(RestClient.builder().requestFactory(timeouts()).build(), URI.create(baseUrl.trim()),
                circuits.create(SERVICE));
    }

    MeteoAlarmClient(RestClient restClient, URI baseUrl, CircuitBreaker breaker) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.breaker = breaker;
    }

    /**
     * The Atom feed of the Italian warnings, as it came.
     *
     * @throws ServiceUnavailableException when the circuit is open or the read fails; the caller keeps its list
     */
    byte[] feed() {
        return get(baseUrl.resolve(FEED));
    }

    /**
     * One CAP document by the link of its feed entry. A link on another host is refused, never fetched: the
     * feed is outside input and its links are too.
     */
    byte[] cap(String capUrl) {
        URI uri = URI.create(capUrl.trim());
        if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(baseUrl.getHost())) {
            log.warn("Refused a MeteoAlarm CAP document outside {}", baseUrl.getHost());
            throw new ServiceUnavailableException("CAP document outside MeteoAlarm");
        }
        return get(uri);
    }

    private byte[] get(URI uri) {
        if (!breaker.allowsCall()) {
            throw new ServiceUnavailableException("MeteoAlarm is not reachable");
        }
        Timer.Sample call = breaker.startCall();
        try {
            byte[] body = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new ServiceUnavailableException("MeteoAlarm sent an empty reply");
            }
            breaker.success(call);
            return body;
        } catch (RuntimeException e) {
            breaker.failure(call);
            // The message may carry the document's address: log the type only (#27).
            log.warn("MeteoAlarm call failed: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    /** Short timeouts: the feed is polled again anyway. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        return timeouts;
    }
}
