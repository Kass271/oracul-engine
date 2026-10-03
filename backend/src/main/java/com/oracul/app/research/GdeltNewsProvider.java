package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GdeltNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(GdeltNewsProvider.class);

    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder()
        .enable(tools.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
    private final String baseUrl;
    private final int maxRecords;
    private final Duration timeout;

    GdeltNewsProvider(@Value("${oracul.news.gdelt.base-url:https://api.gdeltproject.org}") String baseUrl,
                      @Value("${oracul.news.max-records-per-query:25}") int maxRecords,
                      @Value("${oracul.news.query-timeout:PT10S}") Duration timeout) {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.maxRecords = maxRecords;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** GDELT timespan parameter of a horizon. */
    public static String timespan(HorizonCode horizon) {
        return switch (horizon) {
            case _1D, _1W -> "7d";
            case _1M -> "14d";
            default -> "3months";
        };
    }

    /** Days covered by the timespan, used for the age filter. */
    public static int timespanDays(HorizonCode horizon) {
        return switch (horizon) {
            case _1D, _1W -> 7;
            case _1M -> 14;
            default -> 90;
        };
    }

    @Override
    public Result search(String queryText, HorizonCode horizon) {
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            String url = baseUrl + "/api/v2/doc/doc?query=" + encode(queryText + " sourcelang:english")
                + "&mode=ArtList&format=json&maxrecords=" + maxRecords + "&sort=HybridRel&timespan="
                + timespan(horizon);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Accept", "application/json").GET().build();
            future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() / 100 != 2) {
                log.warn("news query failed: status={}", response.statusCode());
                return Result.failed();
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed();
        } catch (Exception e) {
            if (future != null) {
                future.cancel(true);
            }
            log.warn("news query failed: {}", e.getClass().getSimpleName());
            return Result.failed();
        }
    }

    private Result parse(String body) {
        if (body == null || body.isBlank()) {
            return new Result(SearchQueryStatus.EMPTY, List.of());
        }
        try {
            JsonNode root = json.readTree(body);
            if (!root.isObject()) {
                return Result.failed();
            }
            List<Article> out = new ArrayList<>();
            for (JsonNode a : root.path("articles")) {
                out.add(new Article(text(a, "url"), text(a, "title"), text(a, "domain"), text(a, "language"),
                    text(a, "seendate")));
            }
            return out.isEmpty() ? new Result(SearchQueryStatus.EMPTY, List.of())
                : new Result(SearchQueryStatus.OK, out);
        } catch (Exception e) {
            log.warn("news answer is not JSON");
            return Result.failed();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isString() ? v.asString() : null;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
