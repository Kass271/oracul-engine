package com.oracul.app.reasoning;

/** The exact value of the request body key "text" of every SCENARIO_GENERATION call (strict structured output). */
final class ScenarioSchema {

    static final String TEXT_FORMAT_JSON =
        "{\"format\":{\"type\":\"json_schema\",\"name\":\"structured_scenario\",\"strict\":true,\"schema\":{\"type" +
        "\":\"object\",\"additionalProperties\":false,\"required\":[\"candidateFutures\",\"factsUsed\",\"infere" +
        "nces\",\"speculations\",\"counterSignalsConsidered\",\"causalChain\",\"futureEvent\",\"unknowns\"],\"p" +
        "roperties\":{\"candidateFutures\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalPropert" +
        "ies\":false,\"required\":[\"title\",\"summary\",\"evaluation\",\"selected\"],\"properties\":{\"title\":{\"" +
        "type\":\"string\"},\"summary\":{\"type\":\"string\"},\"evaluation\":{\"type\":\"string\"},\"selected\":{\"ty" +
        "pe\":\"boolean\"}}}},\"factsUsed\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperti" +
        "es\":false,\"required\":[\"id\",\"statement\",\"evidenceIds\"],\"properties\":{\"id\":{\"type\":\"string\"}" +
        ",\"statement\":{\"type\":\"string\"},\"evidenceIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}" +
        ",\"inferences\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"requi" +
        "red\":[\"id\",\"statement\",\"basedOn\",\"evidenceIds\"],\"properties\":{\"id\":{\"type\":\"string\"},\"stat" +
        "ement\":{\"type\":\"string\"},\"basedOn\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"evidenceIds" +
        "\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}},\"speculations\":{\"type\":\"array\",\"items\":{\"t" +
        "ype\":\"object\",\"additionalProperties\":false,\"required\":[\"id\",\"statement\",\"basedOn\"],\"proper" +
        "ties\":{\"id\":{\"type\":\"string\"},\"statement\":{\"type\":\"string\"},\"basedOn\":{\"type\":\"array\",\"ite" +
        "ms\":{\"type\":\"string\"}}}}},\"counterSignalsConsidered\":{\"type\":\"array\",\"items\":{\"type\":\"obje" +
        "ct\",\"additionalProperties\":false,\"required\":[\"evidenceId\",\"howAddressed\"],\"properties\":{\"e" +
        "videnceId\":{\"type\":\"string\"},\"howAddressed\":{\"type\":\"string\"}}}},\"causalChain\":{\"type\":\"ar" +
        "ray\",\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"order\",\"informatio" +
        "nClass\",\"claimId\",\"statement\",\"evidenceIds\",\"year\"],\"properties\":{\"order\":{\"type\":\"integer" +
        "\"},\"informationClass\":{\"type\":\"string\",\"enum\":[\"FACT\",\"INFERENCE\",\"SPECULATION\",\"FUTURE_EV" +
        "ENT\"]},\"claimId\":{\"type\":[\"string\",\"null\"]},\"statement\":{\"type\":\"string\"},\"evidenceIds\":{\"" +
        "type\":\"array\",\"items\":{\"type\":\"string\"}},\"year\":{\"type\":[\"integer\",\"null\"]}}}},\"futureEven" +
        "t\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"title\",\"summary\",\"date\"],\"pr" +
        "operties\":{\"title\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"},\"date\":{\"type\":\"string\"}}" +
        "},\"unknowns\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}}";

    private ScenarioSchema() {
    }
}
