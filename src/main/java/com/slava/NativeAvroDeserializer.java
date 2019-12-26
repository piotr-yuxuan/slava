package com.slava;

import org.apache.avro.Schema;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer;

import static com.slava.NativeAvroSerdeConfig.ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG;

public class NativeAvroDeserializer extends AbstractKafkaAvroDeserializer implements Deserializer<Map> {

    private Conversion conversionStrategy;

    /**
     * Constructor used by Kafka consumer.
     */
    public NativeAvroDeserializer() {

    }

    public NativeAvroDeserializer(SchemaRegistryClient client) {
        schemaRegistry = client;
    }

    private void configure(Map<String, ?> configs) {
        configure(deserializerConfig(configs));
        NativeAvroSerdeConfig nativeConfig = new NativeAvroSerdeConfig(configs);

        Class<Conversion> conversionStrategyClass = (Class<Conversion>) nativeConfig.getClass(ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG);
        try {
            conversionStrategy = conversionStrategyClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public NativeAvroDeserializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(configs);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(configs);
    }

    private ByteBuffer getByteBuffer(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.get() != MAGIC_BYTE) {
            throw new SerializationException("Unknown magic byte!");
        }
        return buffer;
    }

    @Override
    public Map deserialize(String topic, byte[] data) {
        Schema readerSchema = null;
        try {
            readerSchema = schemaRegistry.getBySubjectAndId(topic, getByteBuffer(data).getInt());
        } catch (IOException | RestClientException e) {
            e.printStackTrace();
        }
        assert readerSchema != null;
        return deserialize(topic, data, readerSchema);
    }

    /**
     * Pass a reader schema to get an Avro projection
     */
    public Map deserialize(String topic, byte[] bytes, Schema readerSchema) {
        return (Map) conversionStrategy.fromAvro(readerSchema, deserialize(bytes, readerSchema));
    }
}
