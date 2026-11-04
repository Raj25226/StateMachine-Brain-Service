package com.example.statemachine.enums;

import com.example.statemachine.constants.SqlQueryConstants;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Loads the packaged SQL catalog once and resolves queries using a parent group and constant suffix. */
public enum SqlQueryFunction implements BiFunction<String, String, String> {
    GET_QUERY;

    private final Map<String, String> queriesByKey;

    SqlQueryFunction() {
        YamlPropertiesFactoryBean yamlLoader = new YamlPropertiesFactoryBean();
        yamlLoader.setResources(new ClassPathResource(SqlQueryConstants.RESOURCE_NAME));
        Properties queryProperties = yamlLoader.getObject();
        if (queryProperties == null || queryProperties.isEmpty()) {
            throw new IllegalStateException("SQL query catalog is empty: " + SqlQueryConstants.RESOURCE_NAME);
        }
        queriesByKey = Map.copyOf(queryProperties.stringPropertyNames().stream()
                .collect(Collectors.toMap(Function.identity(), queryProperties::getProperty)));
    }

    @Override
    public String apply(String parent, String querySuffix) {
        if (!StringValidationPredicate.NON_BLANK.test(parent)
                || !StringValidationPredicate.NON_BLANK.test(querySuffix)) {
            throw new IllegalArgumentException("SQL query parent and suffix must be nonblank.");
        }
        String queryKey = parent + querySuffix;
        String query = queriesByKey.get(queryKey);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("SQL query is missing or blank: " + queryKey);
        }
        return query;
    }
}
