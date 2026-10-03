package com.oracul.app.research;

import com.oracul.app.api.model.SourceType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Source type and quality from the publisher domain (research-pipeline.md FR-13). */
@Component
public class SourceQualityTable {

    public record Classification(SourceType type, double quality) {
    }

    private final List<String> official;
    private final List<String> research;
    private final List<String> established;
    private final List<String> blogs;

    public SourceQualityTable(
        @Value("${oracul.news.quality.official-domains:who.int,europa.eu,un.org,worldbank.org,imf.org,oecd.org}") String official,
        @Value("${oracul.news.quality.research-domains:nature.com,science.org,thelancet.com,nejm.org,cell.com,arxiv.org,sciencedirect.com,pnas.org,bmj.com}") String research,
        @Value("${oracul.news.quality.established-news-domains:reuters.com,apnews.com,bbc.com,bbc.co.uk,theguardian.com,nytimes.com,washingtonpost.com,ft.com,bloomberg.com,economist.com,wsj.com,npr.org,aljazeera.com,cnn.com,dw.com,france24.com}") String established,
        @Value("${oracul.news.quality.blog-domains:medium.com,substack.com,blogspot.com,wordpress.com,tumblr.com}") String blogs) {
        this.official = split(official);
        this.research = split(research);
        this.established = split(established);
        this.blogs = split(blogs);
    }

    public Classification classify(String domain) {
        String d = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("www.")) {
            d = d.substring(4);
        }
        if (d.isEmpty()) {
            return new Classification(SourceType.OTHER, 0.4);
        }
        if (d.endsWith(".gov") || d.endsWith(".mil") || d.endsWith(".int") || d.contains(".gov.")
            || matches(d, official)) {
            return new Classification(SourceType.OFFICIAL, 0.95);
        }
        if (d.endsWith(".edu") || d.contains(".ac.") || matches(d, research)) {
            return new Classification(SourceType.RESEARCH, 0.9);
        }
        if (matches(d, established)) {
            return new Classification(SourceType.NEWS, 0.85);
        }
        boolean blogLabel = Arrays.stream(d.split("\\.")).anyMatch(l -> l.equals("blog") || l.startsWith("blog"));
        if (blogLabel || matches(d, blogs)) {
            return new Classification(SourceType.BLOG, 0.35);
        }
        return new Classification(SourceType.NEWS, 0.6);
    }

    private static boolean matches(String domain, List<String> list) {
        return list.stream().anyMatch(x -> domain.equals(x) || domain.endsWith("." + x));
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
            .toList();
    }
}
