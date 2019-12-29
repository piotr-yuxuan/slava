package com.slava;

import org.apache.avro.Schema;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;

import static com.slava.NativeAvroSerdeConfig.COM_SLAVA_CONVERSION_CLASS_CONFIG;
import static com.slava.NativeAvroSerdeConfig.ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG;

public class NativeAvroSerializer extends AbstractKafkaAvroSerializer implements Serializer<Map> {
    private Object nativeAvroSchemaKey;
    private boolean isKey;
    private KafkaAvroSerializer inner;
    private Conversion conversion;

    /**
     * Constructor used by Kafka producer.
     */
    public NativeAvroSerializer() {
        inner = new KafkaAvroSerializer();
    }

    public NativeAvroSerializer(SchemaRegistryClient client) {
        schemaRegistry = client;
        inner = new KafkaAvroSerializer(client);
    }

    private void configure(Map<String, ?> configs) {
        configure(serializerConfig(configs));
        NativeAvroSerdeConfig nativeConfig = new NativeAvroSerdeConfig(configs);
        nativeAvroSchemaKey = nativeConfig.getString(ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG);

        Class<Conversion> conversionClass = (Class<Conversion>) nativeConfig.getClass(COM_SLAVA_CONVERSION_CLASS_CONFIG);
        try {
            conversion = conversionClass.getDeclaredConstructor().newInstance();
            conversion.configure(nativeConfig);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public NativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(configs);
        inner = new KafkaAvroSerializer(client, configs);
    }

    public NativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs, KafkaAvroSerializer serializer) {
        schemaRegistry = client;
        configure(configs);
        inner = serializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        configure(configs);
        inner.configure(configs, isKey);
    }

    /**
     * Copied from {@link AbstractKafkaAvroSerDe} because it's private.
     */
    public SubjectNameStrategy subjectNameStrategy(boolean isKey) {
        return isKey ? (SubjectNameStrategy) keySubjectNameStrategy : (SubjectNameStrategy) valueSubjectNameStrategy;
    }

    @Override
    public byte[] serialize(String topic, Map map) {
        Schema schema = getSchema(topic, map);
        return inner.serialize(topic, conversion.toAvro(schema, map));
    }

    @Override
    public void close() {
        inner.close();
    }

    private Schema getSchema(String topic, Map map) {
        // Be able to choose the behaviour
        Schema schema = getSchemaFromRegistry(topic);
        if (schema == null)
            schema = getSchemaFromMap(map);
        return schema;
    }

    private Schema getSchemaFromRegistry(String topic) {
        Schema schema = null;
        String subject = this.subjectNameStrategy(isKey).subjectName(topic, isKey, (Schema) null);
        try {
            schema = (new Schema.Parser()).parse(schemaRegistry.getLatestSchemaMetadata(subject).getSchema());
        } catch (IOException | RestClientException e) {
            e.printStackTrace();
        }
        return schema;
    }

    private Schema getSchemaFromMap(Map map) {
        // TODO also allow class reference so you can use specificRecord classes.
        return (Schema) map.get(nativeAvroSchemaKey);
    }
}
