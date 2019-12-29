package com.slava;

import org.apache.avro.Schema;

public interface Conversion {

    void configure(NativeAvroSerdeConfig config);

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
}
