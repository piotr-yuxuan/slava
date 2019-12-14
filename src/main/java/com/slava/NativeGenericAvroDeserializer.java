package com.slava;

import org.apache.kafka.common.annotation.InterfaceStability.Unstable;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

@Unstable
public class NativeGenericAvroDeserializer implements Deserializer<Map> {
    private final KafkaNativeAvroDeserializer inner;

    public NativeGenericAvroDeserializer() {
        this.inner = new KafkaNativeAvroDeserializer();
    }

    NativeGenericAvroDeserializer(SchemaRegistryClient client) {
        this.inner = new KafkaNativeAvroDeserializer(client);
    }

    public void configure(Map<String, ?> deserializerConfig, boolean isDeserializerForRecordKeys) {
        this.inner.configure(deserializerConfig, isDeserializerForRecordKeys);
    }

    public Map deserialize(String topic, byte[] data) {
        return (Map) this.inner.deserialize(topic, data);
    }

    public void close() {
        this.inner.close();
    }
}
