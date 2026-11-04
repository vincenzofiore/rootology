package io.github.vincenzofiore.rootology.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The UI's texts (#31): both languages have the same keys, every key the code uses exists, no ASCII apostrophe. */
class TranslationsTest {

    static Properties load(String file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = TranslationsTest.class.getResourceAsStream("/i18n/" + file)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void italianAndEnglishHaveTheSameKeysAndNoAsciiApostrophe() throws IOException {
        Properties it = load("messages.properties");
        Properties en = load("messages_en.properties");

        assertThat(new TreeSet<>(en.stringPropertyNames())).isEqualTo(new TreeSet<>(it.stringPropertyNames()));
        assertThat(it.values()).noneMatch(v -> v.toString().contains("'"));
        assertThat(en.values()).noneMatch(v -> v.toString().contains("'"));
    }

    @Test
    void everyKeyTheUiUsesExists() throws IOException {
        Properties it = load("messages.properties");
        Set<String> prefixes = it.stringPropertyNames().stream().map(k -> k.substring(0, k.indexOf('.') + 1))
                .collect(Collectors.toSet());
        Pattern literal = Pattern.compile("\"([a-z]+\\.[a-zA-Z0-9_.\\-]+)\"");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/io/github/vincenzofiore/rootology/ui"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (prefixes.stream().anyMatch(key::startsWith) && !key.endsWith(".")) {
                        used.add(key);
                    }
                }
            }
        }
        assertThat(used).isNotEmpty().allMatch(it::containsKey);
    }

    @Test
    void everyValueShownThroughAKeyHasOne() throws IOException {
        Properties it = load("messages.properties");
        Stream<String> keys = Stream.of(
                Stream.of(io.github.vincenzofiore.rootology.spaces.Role.values()).map(v -> "role." + v.name()),
                Stream.of(io.github.vincenzofiore.rootology.garden.PlotKind.values()).map(v -> "plotkind." + v.name()),
                Stream.of(io.github.vincenzofiore.rootology.garden.Soil.values()).map(v -> "soil." + v.name()),
                Stream.of(io.github.vincenzofiore.rootology.garden.PlantingMethod.values()).map(v -> "method." + v.name()),
                Stream.of(io.github.vincenzofiore.rootology.enrichment.ProposalStatus.values())
                        .map(v -> "proposal." + v.name()),
                Stream.of("ALIAS", "PAIR", "HOST").map(v -> "knowledge.kind." + v),
                Stream.of("it", "en").map(v -> "knowledge.language." + v),
                Stream.of("COMPANION", "ANTAGONIST").map(v -> "knowledge.relation." + v)).flatMap(k -> k);

        assertThat(keys).allMatch(it::containsKey);
    }

    @Test
    void italianIsTheDefaultAndEnglishIsKept() {
        Translations translations = new Translations();

        assertThat(translations.getTranslation("nav.ask", Locale.GERMAN)).isEqualTo("Chiedi");
        assertThat(translations.getTranslation("nav.ask", Locale.forLanguageTag("en-GB"))).isEqualTo("Ask");
        assertThat(translations.getTranslation("ask.today", Locale.ITALIAN, 3, 30)).isEqualTo("3 di 30 domande oggi");
        assertThat(translations.getTranslation("no.such.key", Locale.ITALIAN)).isEqualTo("!no.such.key");
    }
}
