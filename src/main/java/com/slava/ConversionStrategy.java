package com.slava;

import org.apache.avro.Schema;

public interface ConversionStrategy {
    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to native type
     */
    Object toConvertedType(Schema schema, Object data);

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to avro type
     */
    Object toAvroType(Schema schema, Object data);
}
