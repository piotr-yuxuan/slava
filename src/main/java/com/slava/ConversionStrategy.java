package com.slava;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import java.util.Optional;

public interface ConversionStrategy {

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to native type
     */
    Object fromAvro(Schema schema, Object data);

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to avro type
     */
    Object toAvro(Schema schema, Object data);

    interface Dispatch {
        static String schemaName(Schema schema, Object object) {
            return schema.getFullName();
        }

        static String logicalType(Schema schema, Object object) {
            return Optional.ofNullable(schema)
                    .map(Schema::getLogicalType)
                    .map(LogicalType::getName)
                    .orElseGet(() -> null);
        }

        static Schema.Type schemaType(Schema schema, Object data) {
            return schema.getType();
        }
    }
}
