package com.slava;

import java.util.Map;

import io.confluent.common.config.ConfigDef;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;

public class KafkaNativeAvroDeserializerConfig extends AbstractKafkaAvroSerDeConfig {

    public static final String ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG = "org.apache.avro.conversion.strategy";
    // If you do want a default, Java-Clojure interop requires it be written in Java.
    public static final String ORG_APACHE_AVRO_CONVERSION_STRATEGY_DOC = "org.apache.avro.conversion.strategy";

    private static ConfigDef config;

    public KafkaNativeAvroDeserializerConfig(Map<?, ?> props) {
        super(config, props);
    }

    static {
        config = baseConfigDef()
                .define(ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG,
                        ConfigDef.Type.CLASS,
                        ConfigDef.Importance.MEDIUM,
                        ORG_APACHE_AVRO_CONVERSION_STRATEGY_DOC);
    }
}
