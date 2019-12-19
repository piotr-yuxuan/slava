package com.slava;

import org.apache.avro.Schema;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;

import static com.slava.KafkaNativeAvroSerializerConfig.ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG;
import static com.slava.KafkaNativeAvroSerializerConfig.ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG;

public class KafkaNativeAvroSerializer extends AbstractKafkaAvroSerializer implements Serializer<Map> {
    private Object nativeAvroSchemaKey;
    private boolean isKey;
    private KafkaAvroSerializer inner;
    private ConversionStrategy conversionStrategy;

    /**
     * Constructor used by Kafka producer.
     */
    public KafkaNativeAvroSerializer() {
        inner = new KafkaAvroSerializer();
    }

    public KafkaNativeAvroSerializer(SchemaRegistryClient client) {
        schemaRegistry = client;
        inner = new KafkaAvroSerializer(client);
    }

    private void configure(Map<String, ?> configs) {
        configure(serializerConfig(configs));
        nativeAvroSchemaKey = configs.get(ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG);
        try {
            conversionStrategy = ((Class<ConversionStrategy>) Class.forName((String) configs.get(ORG_APACHE_AVRO_CONVERSION_STRATEGY_CONFIG))).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public KafkaNativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(configs);
        inner = new KafkaAvroSerializer(client, configs);
    }

    public KafkaNativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs, KafkaAvroSerializer serializer) {
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
    private SubjectNameStrategy subjectNameStrategy(boolean isKey) {
        return isKey ? (SubjectNameStrategy) keySubjectNameStrategy : (SubjectNameStrategy) valueSubjectNameStrategy;
    }

    @Override
    public byte[] serialize(String topic, Map map) {
        Schema schema = getSchemaFromMap(map); // TODO getSchema(topic, map);
        return inner.serialize(topic, conversionStrategy.toAvroType(schema, map));
    }

    @Override
    public void close() {
        inner.close();
    }

    private Schema getSchema(String topic, Map map) {
        Schema schema = null;
        if (subjectNameStrategy(isKey) instanceof TopicNameStrategy) {
            schema = getSchemaFromRegistry(topic);
        } else {
            schema = getSchemaFromMap(map);
        }
        return schema;
    }

    private Schema getSchemaFromRegistry(String topic) {
        Schema schema = null;
        String subject = this.subjectNameStrategy(isKey).subjectName(topic, isKey, null);
        try {
            List<Integer> versions = schemaRegistry.getAllVersions(subject);
            int latestVersion = versions.get(versions.size() - 1);
            schema = schemaRegistry.getBySubjectAndId(subject, latestVersion);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RestClientException e) {
            e.printStackTrace();
        }
        return schema;
    }

    private Schema getSchemaFromMap(Map map) {
        // TODO also allow class reference so you can use specificRecord classes.
        return (Schema) map.get(nativeAvroSchemaKey);
    }
}
