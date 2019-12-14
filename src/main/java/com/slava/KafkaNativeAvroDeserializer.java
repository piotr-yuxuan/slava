package com.slava;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

public class KafkaNativeAvroDeserializer extends AbstractKafkaAvroDeserializer
        implements Deserializer<Map> {

    /**
     * Constructor used by Kafka consumer.
     */
    public KafkaNativeAvroDeserializer() {

    }

    public KafkaNativeAvroDeserializer(SchemaRegistryClient client) {
        schemaRegistry = client;
    }

    public KafkaNativeAvroDeserializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(deserializerConfig(configs));
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(new KafkaAvroDeserializerConfig(configs));
    }

    @Override
    public Map deserialize(String topic, byte[] data) {
        return coerceGenericRecordToMap((GenericRecord) deserialize(data));
    }

    /**
     * Pass a reader schema to get an Avro projection
     */
    public Map deserialize(String s, byte[] bytes, Schema readerSchema) {
        return coerceGenericRecordToMap((GenericRecord) deserialize(bytes, readerSchema));
    }

    private Map coerceGenericRecordToMap(GenericRecord record) {
        Map<String, Object> map = new HashMap<>();
        for (Schema.Field field : record.getSchema().getFields()) {
            map.put(field.name(), record.get(field.name()));
        }
        return Collections.unmodifiableMap(map);
    }
}
