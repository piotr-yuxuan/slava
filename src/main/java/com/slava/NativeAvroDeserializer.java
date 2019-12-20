package com.slava;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer;

import static com.slava.NativeAvroSerdeConfig.ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG;

public class NativeAvroDeserializer extends AbstractKafkaAvroDeserializer implements Deserializer<Map> {

    private ConversionStrategy conversionStrategy;

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

        Class<ConversionStrategy> conversionStrategyClass = (Class<ConversionStrategy>) nativeConfig.getClass(ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG);
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

    @Override
    public Map deserialize(String topic, byte[] data) {
        GenericRecord record = (GenericRecord) deserialize(data);
        return (Map) conversionStrategy.toConvertedType(record.getSchema(), record);
    }

    /**
     * Pass a reader schema to get an Avro projection
     */
    public Map deserialize(String s, byte[] bytes, Schema readerSchema) {
        return (Map) conversionStrategy.toConvertedType(readerSchema, deserialize(bytes, readerSchema));
    }
}
