package com.slava;

import org.apache.avro.Schema;

public interface ICljAvroTransformer {

    void configure(CljAvroSerdeConfig config);

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced from avro
     */
    Object fromAvroToClj(Schema schema, Object data);

    /**
     * @param schema of the data to be coerced
     * @param data   to coerce
     * @return data coerced to avro
     */
    Object fromCljToAvro(Schema schema, Object data);
}
