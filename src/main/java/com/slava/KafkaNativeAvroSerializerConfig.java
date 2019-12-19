package com.slava;

import java.util.Map;

import io.confluent.common.config.ConfigDef;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;

public class KafkaNativeAvroSerializerConfig extends AbstractKafkaAvroSerDeConfig {

    public static final String ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG = "org.apache.avro.conversion.strategy";
    public static final String ORG_APACHE_AVRO_CONVERSION_STRATEGY_DOC = "org.apache.avro.conversion.strategy";

    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG = "org.apache.avro.schema.key";
    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_DEFAULT = "org.apache.avro.schema.key";
    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_DOC = "org.apache.avro.schema.key";

    private static ConfigDef config;

    public KafkaNativeAvroSerializerConfig(Map<?, ?> props) {
        super(config, props);
    }

    static {
        config = baseConfigDef()
                .define(ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG,
                        ConfigDef.Type.CLASS,
                        ConfigDef.Importance.MEDIUM,
                        ORG_APACHE_AVRO_CONVERSION_STRATEGY_DOC)
                .define(ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG,
                        ConfigDef.Type.STRING,
                        ORG_APACHE_AVRO_SCHEMA_KEY_DEFAULT,
                        ConfigDef.Importance.MEDIUM,
                        ORG_APACHE_AVRO_SCHEMA_KEY_DOC);
    }
}
