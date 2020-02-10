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

import static com.slava.CljAvroSerdeConfig.COM_SLAVA_CONVERSION_CLASS_CONFIG;

public class CljAvroDeserializer extends AbstractKafkaAvroDeserializer implements Deserializer<Map> {

    private ICljAvroTransformer transformer;

    /**
     * Constructor used by Kafka consumer.
     */
    public CljAvroDeserializer() {

    }

    public CljAvroDeserializer(SchemaRegistryClient client) {
        schemaRegistry = client;
    }

    private void configure(Map<String, ?> config) {
        configure(deserializerConfig(config));
        CljAvroSerdeConfig transformerConfig = new CljAvroSerdeConfig(config);

        Class<ICljAvroTransformer> conversionClass = (Class<ICljAvroTransformer>) transformerConfig.getClass(COM_SLAVA_CONVERSION_CLASS_CONFIG);
        try {
            transformer = conversionClass.getDeclaredConstructor().newInstance();
            transformer.configure(transformerConfig);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public CljAvroDeserializer(SchemaRegistryClient client, Map<String, ?> configs) {
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
        return (Map) transformer.fromAvroToClj(readerSchema, deserialize(bytes, readerSchema));
    }
}
