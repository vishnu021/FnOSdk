package com.vish.fno.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

    private final static ObjectMapper mapper = new ObjectMapper();

    public static String getNonFormattedObject(Object order) {
        try {
            return mapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse object: {}", order, e);
        }
        return "";
    }

    public static String getFormattedObject(Object order) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse object: {}", order, e);
        }
        return "";
    }
}
