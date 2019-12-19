package com.slava;

import org.apache.avro.Schema;

import java.util.Map;

public interface ConversionStrategy {
    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to native type
     */
    default Object toNativeType(Schema schema, Object data) {
        return data;
    }

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to avro type
     */
    default Object toAvroType(Schema schema, Map data) {
        return data;
    }
}
