package com.oracul.app.research;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Validated ranking / evidence configuration; invalid values fail startup (research-pipeline.md slice 07). */
@Configuration(proxyBeanMethods = false)
public class EvidenceConfig {

    @Bean
    RankingWeights rankingWeights(
        @Value("${oracul.ranking.weights.topic-match:1.0}") double topicMatch,
        @Value("${oracul.ranking.weights.wildcard-match:1.5}") double wildcardMatch,
        @Value("${oracul.ranking.weights.darkness-match:1.5}") double darknessMatch,
        @Value("${oracul.ranking.weights.optimism-match:1.5}") double optimismMatch,
        @Value("${oracul.ranking.weights.recency:0.75}") double recency,
        @Value("${oracul.ranking.weights.source-quality:1.0}") double sourceQuality,
        @Value("${oracul.ranking.weights.impact:1.0}") double impact,
        @Value("${oracul.ranking.weights.trend-strength:0.5}") double trendStrength,
        @Value("${oracul.ranking.weights.cross-topic:0.5}") double crossTopic,
        @Value("${oracul.ranking.weights.realism-compatibility:1.0}") double realismCompatibility) {
        nonNegative("oracul.ranking.weights.topic-match", topicMatch);
        nonNegative("oracul.ranking.weights.wildcard-match", wildcardMatch);
        nonNegative("oracul.ranking.weights.darkness-match", darknessMatch);
        nonNegative("oracul.ranking.weights.optimism-match", optimismMatch);
        nonNegative("oracul.ranking.weights.recency", recency);
        nonNegative("oracul.ranking.weights.source-quality", sourceQuality);
        nonNegative("oracul.ranking.weights.impact", impact);
        nonNegative("oracul.ranking.weights.trend-strength", trendStrength);
        nonNegative("oracul.ranking.weights.cross-topic", crossTopic);
        nonNegative("oracul.ranking.weights.realism-compatibility", realismCompatibility);
        RankingWeights w = new RankingWeights(topicMatch, wildcardMatch, darknessMatch, optimismMatch, recency,
            sourceQuality, impact, trendStrength, crossTopic, realismCompatibility);
        if (!(w.sum() > 0)) {
            throw new IllegalStateException("oracul.ranking.weights: the sum of all weights must be greater than 0");
        }
        return w;
    }

    @Bean
    EvidenceProperties evidenceProperties(
        @Value("${oracul.evidence.max-items:25}") int maxItems,
        @Value("${oracul.evidence.core:10}") int core,
        @Value("${oracul.evidence.supporting:10}") int supporting,
        @Value("${oracul.evidence.counter-signals:5}") int counterSignals,
        @Value("${oracul.evidence.max-per-entity:2}") int maxPerEntity,
        @Value("${oracul.evidence.max-per-publisher:3}") int maxPerPublisher,
        @Value("${oracul.evidence.max-per-geography:6}") int maxPerGeography,
        @Value("${oracul.evidence.max-category-share:0.4}") double maxCategoryShare,
        @Value("${oracul.ranking.min-source-quality:0.30}") double minSourceQuality) {
        range("oracul.ranking.min-source-quality", minSourceQuality, 0, 1);
        range("oracul.evidence.max-items", maxItems, 1, 100);
        range("oracul.evidence.core", core, 0, 100);
        range("oracul.evidence.supporting", supporting, 0, 100);
        range("oracul.evidence.counter-signals", counterSignals, 0, 100);
        range("oracul.evidence.max-per-entity", maxPerEntity, 1, Integer.MAX_VALUE);
        range("oracul.evidence.max-per-publisher", maxPerPublisher, 1, Integer.MAX_VALUE);
        range("oracul.evidence.max-per-geography", maxPerGeography, 1, Integer.MAX_VALUE);
        if (!(maxCategoryShare > 0 && maxCategoryShare <= 1)) {
            throw new IllegalStateException("oracul.evidence.max-category-share must be > 0 and <= 1");
        }
        if ((long) core + supporting + counterSignals > maxItems) {
            throw new IllegalStateException(
                "oracul.evidence: core + supporting + counter-signals must not exceed oracul.evidence.max-items");
        }
        return new EvidenceProperties(maxItems, core, supporting, counterSignals, maxPerEntity, maxPerPublisher,
            maxPerGeography, maxCategoryShare, minSourceQuality);
    }

    @Bean
    MinCoreThresholds minCoreThresholds(
        @Value("${oracul.evidence.min-core.high:5}") int high,
        @Value("${oracul.evidence.min-core.medium:3}") int medium,
        @Value("${oracul.evidence.min-core.low:1}") int low,
        @Value("${oracul.evidence.core:10}") int core) {
        range("oracul.evidence.min-core.high", high, 0, core);
        range("oracul.evidence.min-core.medium", medium, 0, core);
        range("oracul.evidence.min-core.low", low, 0, core);
        return new MinCoreThresholds(high, medium, low);
    }

    @Bean
    EventRanker eventRanker(RankingWeights weights, EvidenceProperties properties) {
        return new EventRanker(weights, properties.minSourceQuality());
    }

    @Bean
    EvidenceSelector evidenceSelector(EvidenceProperties properties) {
        return new EvidenceSelector(properties);
    }

    private static void nonNegative(String name, double v) {
        if (!(v >= 0)) {
            throw new IllegalStateException(name + " must be >= 0");
        }
    }

    private static void range(String name, double v, double min, double max) {
        if (!(v >= min && v <= max)) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
    }
}
