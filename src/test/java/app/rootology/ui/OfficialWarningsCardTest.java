package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.ClimateWindows;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.history.RunHistory;
import app.rootology.notifications.NotificationService;
import app.rootology.officialwarnings.OfficialWarning;
import app.rootology.officialwarnings.OfficialWarnings;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The card of the official warnings on the garden home (#136): the warnings of the garden's region as MeteoAlarm
 * writes them, with the issuing service, the link and the disclaimer; nothing for a region it cannot know, and
 * nothing shown as current when the feed was not read for too long. The feed is stubbed: no network.
 */
class OfficialWarningsCardTest {

    /** Abruzzo in GeoNames' codes, IT013 as MeteoAlarm's zone. */
    private static final String ABRUZZO = "01";
    private static final String ABRUZZO_ZONE = "IT013";
    private static final String HEADLINE = "Allerta arancione per vento";
    private static final String AREA = "APPENNINO E COSTE";

    @Test
    void anOrangeWindWarningShowsItsAreaTheHeadlineItsIssuerTheLinkAndTheDisclaimer() {
        OfficialWarnings official = mock(OfficialWarnings.class);
        when(official.stale()).thenReturn(false);
        when(official.active(ABRUZZO_ZONE)).thenReturn(List.of(warning()));

        Div card = card(garden("IT", ABRUZZO), official);

        assertThat(all(card).filter(H4.class::isInstance).map(Component::getElement)
                .map(element -> element.getTextRecursively()).toList())
                .as("the card's title").singleElement().asString().contains("!{home.official}!");
        Div row = all(card).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.getClassNames().contains("rootology-official-warning"))
                .findFirst().orElseThrow();
        assertThat(row.getClassNames()).as("an orange warning").contains("rootology-official-orange");
        assertThat(spans(row))
                .as("the headline, the area the issuer names under it, its period and the issuer line")
                .containsExactly(HEADLINE, "!{home.official.area}!", "24 nov, 11:00 – 25 nov, 01:00",
                        "!{home.official.issued}!");
        assertThat(all(row).filter(SvgIcon.class::isInstance).map(SvgIcon.class::cast)
                .map(icon -> icon.getElement().getAttribute("aria-label") + " " + icon.getSrc()).toList())
                .as("the warning triangle first, then the phenomenon's drawing, each named for a screen reader")
                .containsExactly("!{warning.level.orange}! icons/alerts/warning.svg",
                        "!{warning.type.wind}! icons/alerts/wind.svg");
        assertThat(all(card).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .map(Anchor::getHref).toList())
                .as("MeteoAlarm asks for its link").containsExactly("https://meteoalarm.org");
        assertThat(paragraphs(card))
                .as("and its disclaimer: our translation, then MeteoAlarm's text as it is (#175)")
                .containsExactly("!{home.official.disclaimer}!", GardenHomeView.METEOALARM_DISCLAIMER);
    }

    @Test
    void aWarningThatNamesNoAreaShowsNoAreaLine() {
        OfficialWarnings official = mock(OfficialWarnings.class);
        when(official.stale()).thenReturn(false);
        when(official.active(ABRUZZO_ZONE)).thenReturn(List.of(withoutArea()));

        Div row = all(card(garden("IT", ABRUZZO), official)).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.getClassNames().contains("rootology-official-warning"))
                .findFirst().orElseThrow();

        assertThat(spans(row))
                .as("only the headline, its period and the issuer line: the area is never guessed")
                .containsExactly(HEADLINE, "24 nov, 11:00 – 25 nov, 01:00", "!{home.official.issued}!");
    }

    @Test
    void aFeedNotReadForTooLongSaysSoAndShowsNoWarning() {
        OfficialWarnings official = mock(OfficialWarnings.class);
        when(official.stale()).thenReturn(true);
        when(official.active(ABRUZZO_ZONE)).thenReturn(List.of(warning()));

        Div card = card(garden("IT", ABRUZZO), official);

        assertThat(paragraphs(card)).as("never a stale warning as if it were current")
                .contains("!{home.official.unavailable}!");
        assertThat(all(card)).noneMatch(component -> component.getClassNames()
                .contains("rootology-official-warning"));
        assertThat(all(card).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .map(Anchor::getHref).toList()).containsExactly("https://meteoalarm.org");
    }

    @Test
    void aGardenAbroadOrWithoutARegionHasNoCard() {
        OfficialWarnings official = mock(OfficialWarnings.class);
        when(official.stale()).thenReturn(true);

        for (GardenSummary garden : List.of(garden("FR", ABRUZZO), garden("IT", null), garden(null, null))) {
            assertThat(all(page(garden, official)).map(Component::getElement)
                    .map(element -> element.getTextRecursively()).toList())
                    .as("no zone, so no card: %s %s", garden.countryCode(), garden.regionCode())
                    .noneMatch(text -> text.contains("home.official"));
        }
        verify(official, never()).active(org.mockito.ArgumentMatchers.anyString());

        OfficialWarnings calm = mock(OfficialWarnings.class);
        when(calm.stale()).thenReturn(false);
        assertThat(all(page(garden("IT", ABRUZZO), calm)).map(Component::getElement)
                .map(element -> element.getTextRecursively()).toList())
                .as("a region with no active warning, like the computed alerts").noneMatch(
                        text -> text.contains("home.official"));
    }

    /** A card of the home, built by the page for one garden: the page itself needs a browser router. */
    private static Div card(GardenSummary garden, OfficialWarnings official) {
        return all(page(garden, official)).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(OfficialWarningsCardTest::isCard).findFirst().orElseThrow();
    }

    private static GardenHomeView page(GardenSummary garden, OfficialWarnings official) {
        GardenHomeView view = view(official);
        view.officialWarningsCard(garden);
        return view;
    }

    private static GardenHomeView view(OfficialWarnings official) {
        return new GardenHomeView(mock(GardenService.class), mock(ClimateCells.class), mock(ClimateWindows.class),
                mock(app.rootology.facts.CropCatalog.class), mock(WeatherService.class), mock(NotificationService.class), official, mock(GardenPipeline.class),
                mock(RunHistory.class), mock(CurrentUser.class));
    }

    private static GardenSummary garden(String country, String region) {
        return new GardenSummary(1, "Orto", "Pescara", new BigDecimal("42.46"), new BigDecimal("14.21"),
                country, region, 20);
    }

    private static boolean isCard(Component component) {
        return component.getClassNames().contains("rootology-card")
                && all(component).filter(H4.class::isInstance).map(Component::getElement)
                        .anyMatch(element -> element.getTextRecursively().contains("home.official"));
    }

    /** An orange wind warning of Abruzzo, as the feed and its CAP document give it. */
    private static OfficialWarning warning() {
        return new OfficialWarning("warning-1", ABRUZZO_ZONE, "Abruzzo", 3, 1, "Orange Wind Warning", "Severe",
                Instant.parse("2026-11-24T09:30:00Z"), Instant.parse("2026-11-24T10:00:00Z"),
                Instant.parse("2026-11-25T00:00:00Z"), "https://example.invalid/cap.xml",
                "Servizio Meteorologico dell’Aeronautica Militare", HEADLINE,
                "Sono previsti fenomeni meteorologici di moderata intensità " + AREA
                        + " (DISCLAIMER: test)", "Restare in casa.");
    }

    /** The same warning with a description that names no area: only the opening sentence. */
    private static OfficialWarning withoutArea() {
        OfficialWarning warning = warning();
        return new OfficialWarning(warning.identifier(), warning.zone(), warning.areaDesc(), warning.level(),
                warning.type(), warning.event(), warning.severity(), warning.sent(), warning.onset(),
                warning.expires(), warning.capUrl(), warning.senderName(), warning.headline(),
                "Sono previsti fenomeni meteorologici di moderata intensità", warning.instruction());
    }

    /** Every component under this one, the component itself first. */
    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(OfficialWarningsCardTest::all));
    }

    /** The words of the card's own spans: without a Vaadin session a translated text is its key. */
    private static List<String> spans(Component component) {
        return all(component).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText).toList();
    }

    private static List<String> paragraphs(Component component) {
        return all(component).filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText).toList();
    }
}
