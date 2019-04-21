package com.slava;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.annotation.InterfaceStability.Unstable;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

/**
 * Copied from {@link io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde}
 */
@Unstable
public class SpecificAvroSerde<T extends SpecificRecord> implements Serde<T> {
    private final Serde<T> inner;

    public SpecificAvroSerde() {
        this.inner = Serdes.serdeFrom(new SpecificAvroSerializer(), new SpecificAvroDeserializer());
    }

    public SpecificAvroSerde(SchemaRegistryClient client) {
        if (client == null) {
            throw new IllegalArgumentException("schema registry client must not be null");
        } else {
            this.inner = Serdes.serdeFrom(new SpecificAvroSerializer(client), new SpecificAvroDeserializer(client));
        }
    }

    public Serializer<T> serializer() {
        return this.inner.serializer();
    }

    public Deserializer<T> deserializer() {
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
