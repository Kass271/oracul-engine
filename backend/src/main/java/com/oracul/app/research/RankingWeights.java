package com.oracul.app.research;

/** Weights of the ten ranking factors (FR-16). */
public record RankingWeights(double topicMatch, double wildcardMatch, double darknessMatch, double optimismMatch,
                             double recency, double sourceQuality, double impact, double trendStrength,
                             double crossTopic, double realismCompatibility) {

    public static RankingWeights defaults() {
        return new RankingWeights(1.0, 1.5, 1.5, 1.5, 0.75, 1.0, 1.0, 0.5, 0.5, 1.0);
    }

    double sum() {
        return topicMatch + wildcardMatch + darknessMatch + optimismMatch + recency + sourceQuality + impact
            + trendStrength + crossTopic + realismCompatibility;
    }
}
