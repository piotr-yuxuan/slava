package com.slava;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;

public class KafkaNativeAvroSerializer extends AbstractKafkaAvroSerializer implements Serializer<Map> {
    private static final String NATIVE_AVRO_SCHEMA_KEY = "native.avro.schema.key";
    private static final String DEFAULT_NATIVE_AVRO_SCHEMA_KEY = "org.apache.avro.Schema";
    private Object nativeAvroSchemaKey = DEFAULT_NATIVE_AVRO_SCHEMA_KEY;
    private boolean isKey;
    private KafkaAvroSerializer inner;

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

    public KafkaNativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(serializerConfig(configs));
        if (configs.containsKey(NATIVE_AVRO_SCHEMA_KEY)) {
            nativeAvroSchemaKey = configs.get(NATIVE_AVRO_SCHEMA_KEY);
        }
        inner = new KafkaAvroSerializer(client, configs);
    }

    public KafkaNativeAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs, KafkaAvroSerializer serializer) {
        schemaRegistry = client;
        configure(serializerConfig(configs));
        if (configs.containsKey(NATIVE_AVRO_SCHEMA_KEY)) {
            nativeAvroSchemaKey = configs.get(NATIVE_AVRO_SCHEMA_KEY);
        }
        inner = serializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        configure(new KafkaAvroSerializerConfig(configs));
        if (configs.containsKey(NATIVE_AVRO_SCHEMA_KEY)) {
            nativeAvroSchemaKey = configs.get(NATIVE_AVRO_SCHEMA_KEY);
        }
        inner.configure(configs, isKey);
    }

    /**
     * Copied from {@link AbstractKafkaAvroSerDe} because it's private.
     */
    private SubjectNameStrategy subjectNameStrategy(boolean isKey) {
        return isKey ? (SubjectNameStrategy) keySubjectNameStrategy : (SubjectNameStrategy) valueSubjectNameStrategy;
    }

    /**
     * In the general case we can't find the schema only from the topic because the
     * {@link SchemaRegistryClient} subject naming strategy also depends on the schema.
     * <p>
     * However, the default strategy {@link TopicNameStrategy} doesn't depend on the schema. In this
     * case, we could query the {@link SchemaRegistryClient} and retrieve the schema if we could
     * access the strategy… which is a private field.
     * <p>
     * We can't use composition instead of inheritance because {@link Serializer#serialize} must
     * return {@code bytes[]}.
     *
     * @param topic
     * @param map
     * @return
     */
    @Override
    public byte[] serialize(String topic, Map map) {
        Schema schema = getSchemaFromMap(map); // getSchema(topic, map);
        GenericRecordBuilder builder = new GenericRecordBuilder(schema);
        schema.getFields().forEach(field -> {
            if (map.containsKey(field.name())) {
                builder.set(field, map.get(field.name()));
            }
        });
        GenericRecord record = builder.build();
        return inner.serialize(topic, record);
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
        // TODO also allow class reference
        return (Schema) map.get(nativeAvroSchemaKey);
    }
}
