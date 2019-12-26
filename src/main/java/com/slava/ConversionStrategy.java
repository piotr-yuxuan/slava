package com.slava;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;

public interface ConversionStrategy {

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced from avro
     */
    Object fromAvro(Schema schema, Object data);

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to avro
     */
    Object toAvro(Schema schema, Object data);

    interface Dispatch {
        static String schemaName(Schema schema, Object object) {
            return schema.getFullName();
        }

        /**
         * Doesn't return any dispatch value if a {@link Conversion} has been found in
         * {@link GenericData::getConversionFor} for this logical type. This is intended as a way
         * to be a good citizen and play well with this stateful Avro library.
         *
         * @param schema
         * @param object
         * @return a dispatch value, if no {@link Conversion} is known for this logical type
         */
        static String logicalType(Schema schema, Object object) {
            LogicalType logicalType = schema.getLogicalType();
            if (logicalType == null) return null;

            Conversion conversion = GenericData.get().getConversionFor(logicalType);
            if (conversion == null) return logicalType.getName();

            return null;
        }

        static Schema.Type schemaType(Schema schema, Object data) {
            return schema.getType();
        }
    }
}
