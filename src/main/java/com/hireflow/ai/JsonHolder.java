package com.hireflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonHolder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonHolder() { }

    public static ObjectMapper mapper() { return MAPPER; }

    public static String write(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
