package com.slava;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.annotation.InterfaceStability.Unstable;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

/**
 * Copied from {@link io.confluent.kafka.streams.serdes.avro.GenericAvroSerde}
 */
@Unstable
public class GenericAvroSerde implements Serde<GenericRecord> {
    private final Serde<GenericRecord> inner;

    public GenericAvroSerde() {
        this.inner = Serdes.serdeFrom(new GenericAvroSerializer(), new GenericAvroDeserializer());
    }

    public GenericAvroSerde(SchemaRegistryClient client) {
        if (client == null) {
            throw new IllegalArgumentException("schema registry client must not be null");
        } else {
            this.inner = Serdes.serdeFrom(new GenericAvroSerializer(client), new GenericAvroDeserializer(client));
        }
    }

    public Serializer<GenericRecord> serializer() {
        return this.inner.serializer();
    }

    public Deserializer<GenericRecord> deserializer() {
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
