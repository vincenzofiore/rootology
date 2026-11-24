package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.VaadinService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The first steps card (#81), as one row of stages (#177): the progress in the title and four links, each done
 * or to do. The links are built with a mock {@link VaadinService}, the only thing a {@link RouterLink} needs
 * outside a running application.
 */
class FirstStepsCardTest {

    private final Translations translations = new Translations();

    @Test
    void theTitleSaysTheProgressInBothLanguages() {
        assertThat(FirstStepsCard.title(translations.getTranslation("firststeps.title", Locale.ITALIAN),
                translations.getTranslation("firststeps.progress", Locale.ITALIAN, 2, 4)))
                .isEqualTo("Primi passi · 2 di 4");
        assertThat(FirstStepsCard.title(translations.getTranslation("firststeps.title", Locale.ENGLISH),
                translations.getTranslation("firststeps.progress", Locale.ENGLISH, 2, 4)))
                .isEqualTo("First steps · 2 of 4");
    }

    @Test
    void theCardHasItsTitleWithTheProgressAndFourSteps() {
        FirstStepsCard card = card(new FirstSteps(true, true, false, false));

        // Without a Vaadin session getTranslation hands back the key itself (#93).
        assertThat(card.getChildren().filter(H4.class::isInstance).map(H4.class::cast)
                .findFirst().orElseThrow().getText())
                .isEqualTo("!{firststeps.title}! · !{firststeps.progress}!");
        assertThat(links(card)).hasSize(4);
    }

    @Test
    void eachStepIsALinkThatSaysWhetherItIsDone() {
        FirstStepsCard card = card(new FirstSteps(true, true, false, false));

        List<RouterLink> links = links(card);
        assertThat(links).filteredOn(link -> link.getClassNames().contains("rootology-firststeps-done"))
                .hasSize(2);
        assertThat(links).extracting(this::stateOfIcon)
                .containsExactly("!{firststeps.done}!", "!{firststeps.done}!", "!{firststeps.todo}!",
                        "!{firststeps.todo}!");
    }

    @Test
    void everyStepIsDone() {
        FirstStepsCard card = card(new FirstSteps(true, true, true, true));

        assertThat(links(card)).hasSize(4).allSatisfy(link -> assertThat(link.getClassNames())
                .contains("rootology-firststeps-done"));
    }

    /** The card as the garden home builds it, with the service a RouterLink needs to work out its URL. */
    private static FirstStepsCard card(FirstSteps steps) {
        try (MockedStatic<VaadinService> service = mockStatic(VaadinService.class)) {
            VaadinService vaadin = mock(VaadinService.class);
            Router router = mock(Router.class);
            RouteRegistry registry = mock(RouteRegistry.class);
            when(vaadin.getRouter()).thenReturn(router);
            when(router.getRegistry()).thenReturn(registry);
            when(registry.getTargetUrl(any(Class.class), any(RouteParameters.class)))
                    .thenReturn(Optional.of("url"));
            service.when(VaadinService::getCurrent).thenReturn(vaadin);
            return new FirstStepsCard(steps, 1, 2);
        }
    }

    private static List<RouterLink> links(FirstStepsCard card) {
        return card.getChildren().filter(Div.class::isInstance).map(Div.class::cast)
                .flatMap(Div::getChildren).filter(RouterLink.class::isInstance)
                .map(RouterLink.class::cast).toList();
    }

    private String stateOfIcon(RouterLink link) {
        return link.getChildren().filter(Icon.class::isInstance).map(Icon.class::cast)
                .findFirst().orElseThrow().getElement().getAttribute("aria-label");
    }
}