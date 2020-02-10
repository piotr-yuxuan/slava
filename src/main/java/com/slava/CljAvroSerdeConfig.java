package com.slava;

import java.util.Map;

import io.confluent.common.config.ConfigDef;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;

public class CljAvroSerdeConfig extends AbstractKafkaAvroSerDeConfig {

    public static final String COM_SLAVA_CONVERSION_CLASS_CONFIG = "com.slava.conversion.class";
    public static final String COM_SLAVA_CONVERSION_CLASS_STRATEGY_DEFAULT = "com.slava.CljAvroTransformer";
    public static final String COM_SLAVA_CONVERSION_CLASS_STRATEGY_DOC = "TODO";

    public static final String COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_CONFIG = "com.slava.include.schema.in.map";
    public static final boolean COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_DEFAULT = false;
    public static final String COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_DOC = "TODO";

    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG = "org.apache.avro.schema.key";
    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_DEFAULT = "org.apache.avro.schema";
    public static final String ORG_APACHE_AVRO_SCHEMA_KEY_DOC = "TODO";

    public static final String COM_SLAVA_FIELD_NAME_CONVERSION_CONFIG = "com.slava.field.name.conversion";
    public static final String COM_SLAVA_FIELD_NAME_CONVERSION_DEFAULT = "default";
    public static final String COM_SLAVA_FIELD_NAME_CONVERSION_DOC = "TODO";

    public static final String COM_SLAVA_MAP_KEY_CONVERSION_CONFIG = "com.slava.map.key.conversion";
    public static final String COM_SLAVA_MAP_KEY_CONVERSION_DEFAULT = "default";
    public static final String COM_SLAVA_MAP_KEY_CONVERSION_DOC = "TODO";

    public static final String COM_SLAVA_ENUM_CONVERSION_CONFIG = "com.slava.enum.conversion";
    public static final String COM_SLAVA_ENUM_CONVERSION_DEFAULT = "default";
    public static final String COM_SLAVA_ENUM_CONVERSION_DOC = "TODO";

    /**
     * This attribute defines the configuration shape. It handles
     * valid configuration value types and default. As such it is
     * meant to be shared between all instances of CljAvroSerdeConfig,
     * hence the `static`. It is not meant to be used outside of
     * static code of this class, hence the `private`. It should be
     * `final` but it can't because its value needs to be assigned in
     * the static initializer.
     *
     * Clojure code can directly pass a map to clojure-land API.
     *
     * From a technical point of view this class is unnecessary: Java
     * code could pass a raw Map instance which would be handled by
     * clojure namespace internal logic. However I find it a good
     * place to write documentation about config options.
     */
    private static ConfigDef configDefinition;

    public CljAvroSerdeConfig(Map<?, ?> props) {
        super(configDefinition, props);
    }

    static {
        configDefinition = baseConfigDef()
                .define(COM_SLAVA_CONVERSION_CLASS_CONFIG,
                        ConfigDef.Type.CLASS,
                        COM_SLAVA_CONVERSION_CLASS_STRATEGY_DEFAULT,
                        ConfigDef.Importance.HIGH,
                        COM_SLAVA_CONVERSION_CLASS_STRATEGY_DOC)
                .define(COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_CONFIG,
                        ConfigDef.Type.BOOLEAN,
                        COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_DEFAULT,
                        ConfigDef.Importance.LOW,
                        COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_DOC)
                .define(ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG,
                        ConfigDef.Type.STRING,
                        ORG_APACHE_AVRO_SCHEMA_KEY_DEFAULT,
                        ConfigDef.Importance.LOW,
                        ORG_APACHE_AVRO_SCHEMA_KEY_DOC)
                .define(COM_SLAVA_FIELD_NAME_CONVERSION_CONFIG,
                        ConfigDef.Type.STRING,
                        COM_SLAVA_FIELD_NAME_CONVERSION_DEFAULT,
                        ConfigDef.Importance.LOW,
                        COM_SLAVA_FIELD_NAME_CONVERSION_DOC)
                .define(COM_SLAVA_MAP_KEY_CONVERSION_CONFIG,
                        ConfigDef.Type.STRING,
                        COM_SLAVA_MAP_KEY_CONVERSION_DEFAULT,
                        ConfigDef.Importance.LOW,
                        COM_SLAVA_MAP_KEY_CONVERSION_DOC)
                .define(COM_SLAVA_ENUM_CONVERSION_CONFIG,
                        ConfigDef.Type.STRING,
                        COM_SLAVA_ENUM_CONVERSION_DEFAULT,
                        ConfigDef.Importance.LOW,
                        COM_SLAVA_ENUM_CONVERSION_DOC);
    }
}
