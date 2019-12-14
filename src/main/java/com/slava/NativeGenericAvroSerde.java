package com.slava;

import org.apache.kafka.common.annotation.InterfaceStability;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

@InterfaceStability.Unstable
public class NativeGenericAvroSerde implements Serde<Map> {
    private final Serde<Map> inner;

    public NativeGenericAvroSerde() {
        this.inner = Serdes.serdeFrom(new NativeGenericAvroSerializer(), new NativeGenericAvroDeserializer());
    }

    public NativeGenericAvroSerde(SchemaRegistryClient client) {
        if (client == null) {
            throw new IllegalArgumentException("schema registry client must not be null");
        } else {
            this.inner = Serdes.serdeFrom(new NativeGenericAvroSerializer(client), new NativeGenericAvroDeserializer(client));
        }
    }

    public Serializer<Map> serializer() {
        return this.inner.serializer();
    }

    public Deserializer<Map> deserializer() {
        return this.inner.deserializer();
    }

    public void configure(Map<String, ?> serdeConfig, boolean isSerdeForRecordKeys) {
        this.inner.serializer().configure(serdeConfig, isSerdeForRecordKeys);
        this.inner.deserializer().configure(serdeConfig, isSerdeForRecordKeys);
    }

    public void close() {
        this.inner.serializer().close();
        this.inner.deserializer().close();
    }
}
