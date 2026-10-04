package com.oracul.app.research;

import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.common.RawHttpGet;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Google News RSS search (FR-48): one request per group, never retried; the answer is parsed as plain XML (no DTD). */
@Component
public class GoogleNewsProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsProvider.class);
    private static final String ACCEPT = "application/rss+xml, application/xml, text/xml";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";
    private static final List<DateTimeFormatter> DATES = List.of(
        DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss 'GMT'", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC),
        DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss xx", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss zzz", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));

    private final String baseUrl;

    GoogleNewsProvider(@Value("${oracul.news.google.base-url:https://news.google.com}") String baseUrl) {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
    }

    /** {@code (<e1> OR <e2> ...) when:<N>d}; no parentheses around a single element. */
    public static String q(List<String> elements, com.oracul.app.api.model.HorizonCode horizon) {
        String body = elements.size() == 1 ? elements.get(0) : "(" + String.join(" OR ", elements) + ")";
        return body + " when:" + GdeltNewsProvider.timespanDays(horizon) + "d";
    }

    /** Removes one trailing " - <source>" from the title (both trimmed). */
    public static String cleanTitle(String title, String source) {
        if (title == null) {
            return null;
        }
        String t = title.trim();
        if (source != null && !source.isBlank()) {
            String suffix = " - " + source.trim();
            if (t.endsWith(suffix)) {
                return t.substring(0, t.length() - suffix.length()).trim();
            }
        }
        return t;
    }

    /** Never throws for provider problems: they are reported as FAILED. */
    public NewsProvider.Result search(String q, int maxItems, Duration timeout) {
        try {
            if (timeout.isZero() || timeout.isNegative()) {
                return NewsProvider.Result.failed();
            }
            String url = baseUrl + "/rss/search?q=" + encode(q) + "&hl=en-US&gl=US&ceid=US%3Aen";
            RawHttpGet.Response response = RawHttpGet.get(URI.create(url),
                Map.of("Accept", ACCEPT, "User-Agent", USER_AGENT), timeout);
            if (response.status() / 100 != 2) {
                log.warn("google news request failed: status={}", response.status());
                return NewsProvider.Result.failed();
            }
            return parse(response.body(), maxItems);
        } catch (Exception e) {
            log.warn("google news request failed: {}", e.getClass().getSimpleName());
            return NewsProvider.Result.failed();
        }
    }

    static NewsProvider.Result parse(String body, int maxItems) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);
            Document doc = builder.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            Element channel = root == null || !"rss".equals(root.getTagName()) ? null : child(root, "channel");
            if (channel == null) {
                log.warn("google news answer is not RSS");
                return NewsProvider.Result.failed();
            }
            List<NewsProvider.Article> out = new ArrayList<>();
            NodeList nodes = channel.getChildNodes();
            for (int i = 0; i < nodes.getLength() && out.size() < maxItems; i++) {
                Node n = nodes.item(i);
                if (!(n instanceof Element item) || !"item".equals(item.getTagName())) {
                    continue;
                }
                Element source = child(item, "source");
                String sourceText = source == null ? null : source.getTextContent();
                String sourceUrl = source == null || !source.hasAttribute("url") ? null : source.getAttribute("url");
                String title = cleanTitle(text(item, "title"), sourceText);
                out.add(new NewsProvider.Article(text(item, "link"), title, null, null, null,
                    parseDate(text(item, "pubDate")), sourceText == null ? null : sourceText.trim(), sourceUrl, true));
            }
            return out.isEmpty() ? new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of())
                : new NewsProvider.Result(SearchQueryStatus.OK, out);
        } catch (Exception e) {
            log.warn("google news answer is not RSS");
            return NewsProvider.Result.failed();
        }
    }

    static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return ZonedDateTime.parse(raw.trim(), f).toInstant();
            } catch (Exception e) {
                // try the next form
            }
        }
        return null;
    }

    private static Element child(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && name.equals(e.getTagName())) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
