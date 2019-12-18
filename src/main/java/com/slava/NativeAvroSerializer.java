package com.slava;

import org.apache.kafka.common.annotation.InterfaceStability.Unstable;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

@Unstable
public class NativeAvroSerializer implements Serializer<Map> {
    private final KafkaNativeAvroSerializer inner;

    public NativeAvroSerializer() {
        this.inner = new KafkaNativeAvroSerializer();
    }

    NativeAvroSerializer(SchemaRegistryClient client) {
        this.inner = new KafkaNativeAvroSerializer(client);
    }

    NativeAvroSerializer(KafkaNativeAvroSerializer kafkaAvroSerializer) {
        this.inner = kafkaAvroSerializer;
    }

    public void configure(Map<String, ?> serializerConfig, boolean isSerializerForRecordKeys) {
        this.inner.configure(serializerConfig, isSerializerForRecordKeys);
    }

    public byte[] serialize(String topic, Map map) {
        return this.inner.serialize(topic, map);
    }

    public void close() {
        this.inner.close();
    }
}
