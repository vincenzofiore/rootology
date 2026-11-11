package app.rootology.ui;

import com.vaadin.flow.i18n.I18NProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The UI's texts (#31), in Italian and English, from `i18n/messages.properties` (Italian, also the fallback) and
 * `i18n/messages_en.properties`. Vaadin gives each page the browser's language when it is one of these, Italian
 * otherwise. Every text goes through MessageFormat (`{0}` for parameters), so texts use the typographic
 * apostrophe ’ and never ', which MessageFormat would swallow. A missing key shows as `!key`, seen at once.
 */
@Component
public class Translations implements I18NProvider {

    static final Locale ITALIAN = Locale.ITALIAN;
    static final String BUNDLE = "i18n/messages";

    private static final Logger log = LoggerFactory.getLogger(Translations.class);
    private static final ResourceBundle.Control NO_SYSTEM_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Override
    public List<Locale> getProvidedLocales() {
        return List.of(ITALIAN, Locale.ENGLISH);
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        Locale language = locale != null && locale.getLanguage().equals("en") ? Locale.ENGLISH : ITALIAN;
        try {
            String pattern = ResourceBundle.getBundle(BUNDLE, language, NO_SYSTEM_FALLBACK).getString(key);
            return new MessageFormat(pattern, language).format(params);
        } catch (MissingResourceException e) {
            log.warn("Missing translation {} for {}", key, language);
            return "!" + key;
        }
    }
}
