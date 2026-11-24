package app.rootology.officialwarnings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads MeteoAlarm's Atom feed and its CAP documents (#136). Pure and package-private: it only turns bytes into
 * warnings, the client fetches them and {@link OfficialWarnings} keeps them. An entry of the feed that cannot be
 * read is skipped with a log line, never fails the whole feed; a CAP document is read in Italian when it has it.
 * <p>
 * Both documents come from outside: the JDK's parser refuses a DTD outright and loads no external entity.
 */
final class MeteoAlarmFeed {

    private static final Logger log = LoggerFactory.getLogger(MeteoAlarmFeed.class);

    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String CAP = "urn:oasis:names:tc:emergency:cap:1.2";
    private static final String ITALIAN = "it-IT";
    private static final String EMMA_ID = "EMMA_ID";

    private MeteoAlarmFeed() {
    }

    /** The Italian text of a warning, as its CAP document gives it. */
    record CapText(int level, int type, String senderName, String headline, String description, String instruction) {
    }

    /**
     * The warnings of the feed, in the order of its entries. Each entry gives one warning, with its first
     * {@code EMMA_ID} as the zone; level, type and the Italian text stay empty until its CAP document is read.
     *
     * @throws IOException when the document itself cannot be read (not XML, or a DTD)
     */
    static List<OfficialWarning> parseFeed(byte[] xml) throws IOException {
        NodeList entries = parse(xml).getElementsByTagNameNS(ATOM, "entry");
        List<OfficialWarning> warnings = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            try {
                warnings.add(entry((Element) entries.item(i)));
            } catch (RuntimeException e) {
                // The feed carries warnings of several services: one unreadable entry must not hide the others.
                log.warn("Skipped a MeteoAlarm entry that cannot be read: {}", e.getClass().getSimpleName());
            }
        }
        return List.copyOf(warnings);
    }

    /**
     * The Italian text of a CAP document: the {@code info} block in {@code it-IT}, or its first one.
     *
     * @throws IOException when the document cannot be read (not XML, a DTD, no {@code info})
     */
    static CapText parseCap(byte[] xml) throws IOException {
        NodeList infos = parse(xml).getElementsByTagNameNS(CAP, "info");
        if (infos.getLength() == 0) {
            throw new IOException("CAP document without an info block");
        }
        Element chosen = null;
        for (int i = 0; i < infos.getLength() && chosen == null; i++) {
            Element info = (Element) infos.item(i);
            if (ITALIAN.equals(text(info, CAP, "language"))) {
                chosen = info;
            }
        }
        if (chosen == null) {
            chosen = (Element) infos.item(0);
        }
        return new CapText(awareness(chosen, "awareness_level"), awareness(chosen, "awareness_type"),
                text(chosen, CAP, "senderName"), text(chosen, CAP, "headline"),
                text(chosen, CAP, "description"), text(chosen, CAP, "instruction"));
    }

    /** The same warning with the text and the level of its CAP document. */
    static OfficialWarning withText(OfficialWarning warning, CapText text) {
        return new OfficialWarning(warning.identifier(), warning.zone(), warning.areaDesc(), text.level(), text.type(),
                warning.event(), warning.severity(), warning.sent(), warning.onset(), warning.expires(),
                warning.capUrl(), text.senderName(), text.headline(), text.description(), text.instruction());
    }

    /** The text one warning already carries, so a warning reused by a later read is not fetched again. */
    static CapText textOf(OfficialWarning warning) {
        return new CapText(warning.level(), warning.type(), warning.senderName(), warning.headline(),
                warning.description(), warning.instruction());
    }

    private static OfficialWarning entry(Element entry) {
        String zone = null;
        NodeList geocodes = entry.getElementsByTagNameNS(CAP, "geocode");
        for (int i = 0; i < geocodes.getLength() && zone == null; i++) {
            Element geocode = (Element) geocodes.item(i);
            if (EMMA_ID.equals(text(geocode, ATOM, "valueName"))) {
                zone = text(geocode, ATOM, "value");
            }
        }
        if (zone == null) {
            throw new IllegalArgumentException("entry without an " + EMMA_ID);
        }
        String identifier = required(entry, CAP, "identifier");
        return new OfficialWarning(identifier, zone, text(entry, CAP, "areaDesc"), 0, 0,
                text(entry, CAP, "event"), text(entry, CAP, "severity"), time(entry, "sent"),
                time(entry, "onset"), time(entry, "expires"), capUrl(entry), null, null, null, null);
    }

    /** The entry's link to the full CAP document, the only links MeteoAlarm gives besides its web pages. */
    private static String capUrl(Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            if ("application/cap+xml".equals(link.getAttribute("type"))) {
                String href = link.getAttribute("href").trim();
                if (!href.isEmpty()) {
                    return href;
                }
            }
        }
        throw new IllegalArgumentException("entry without a CAP document link");
    }

    private static Instant time(Element entry, String name) {
        String value = required(entry, CAP, name);
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " is not a time", e);
        }
    }

    private static String required(Element parent, String ns, String localName) {
        String text = text(parent, ns, localName);
        if (text == null) {
            throw new IllegalArgumentException("entry without " + localName);
        }
        return text;
    }

    /** The first {@code awareness_level} or {@code awareness_type} parameter, as its number alone. */
    private static int awareness(Element info, String name) {
        NodeList parameters = info.getElementsByTagNameNS(CAP, "parameter");
        for (int i = 0; i < parameters.getLength(); i++) {
            Element parameter = (Element) parameters.item(i);
            if (name.equals(text(parameter, CAP, "valueName"))) {
                String value = text(parameter, CAP, "value");
                if (value != null) {
                    try {
                        // "2; yellow; Moderate" or "10; Rain": the number is what the app shows.
                        return Integer.parseInt(value.split(";", 2)[0].trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private static String text(Element parent, String ns, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() ? null : text;
    }

    private static Document parse(byte[] xml) throws IOException {
        try {
            return documentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (SAXException e) {
            throw new IOException("Not a readable XML document", e);
        }
    }

    private static DocumentBuilder documentBuilder() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // The default handler prints to stderr; here a bad document only fails the read.
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // A warning is not a reason to drop the document.
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return builder;
        } catch (ParserConfigurationException e) {
            throw new IOException("No secure XML parser", e);
        }
    }
}
