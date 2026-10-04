package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.common.RawHttpGet;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GdeltNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(GdeltNewsProvider.class);

    private final JsonMapper json = JsonMapper.builder()
        .enable(tools.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
    private final String baseUrl;

    GdeltNewsProvider(@Value("${oracul.news.gdelt.base-url:https://api.gdeltproject.org}") String baseUrl) {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
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
    public Result search(String query, int maxRecords, HorizonCode horizon, Duration timeout) {
        try {
            if (timeout.isZero() || timeout.isNegative()) {
                return Result.failed();
            }
            String url = baseUrl + "/api/v2/doc/doc?query=" + encode(query)
                + "&mode=ArtList&format=json&maxrecords=" + maxRecords + "&sort=HybridRel&timespan="
                + timespan(horizon);
            RawHttpGet.Response response = RawHttpGet.get(URI.create(url), Map.of("Accept", "application/json"), timeout);
            if (response.status() == 429) {
                log.warn("news request failed: status=429");
                return Result.limited();
            }
            if (response.status() / 100 != 2) {
                log.warn("news request failed: status={}", response.status());
                return Result.failed();
            }
            return parse(response.body());
        } catch (Exception e) {
            log.warn("news request failed: {}", e.getClass().getSimpleName());
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
