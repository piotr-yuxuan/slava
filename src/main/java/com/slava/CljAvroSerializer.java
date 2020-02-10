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

import static com.slava.CljAvroSerdeConfig.COM_SLAVA_CONVERSION_CLASS_CONFIG;
import static com.slava.CljAvroSerdeConfig.ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG;

public class CljAvroSerializer extends AbstractKafkaAvroSerializer implements Serializer<Map> {
    private Object nativeAvroSchemaKey;
    private boolean isKey;
    private KafkaAvroSerializer inner;
    private ICljAvroTransformer transformer;

    /**
     * Constructor used by Kafka producer.
     */
    public CljAvroSerializer() {
        inner = new KafkaAvroSerializer();
    }

    public CljAvroSerializer(SchemaRegistryClient client) {
        schemaRegistry = client;
        inner = new KafkaAvroSerializer(client);
    }

    private void configure(Map<String, ?> config) {
        configure(serializerConfig(config));
        CljAvroSerdeConfig transformerConfig = new CljAvroSerdeConfig(config);
        nativeAvroSchemaKey = transformerConfig.getString(ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG);

        Class<ICljAvroTransformer> transformerClass = (Class<ICljAvroTransformer>) transformerConfig.getClass(COM_SLAVA_CONVERSION_CLASS_CONFIG);
        try {
            transformer = transformerClass.getDeclaredConstructor().newInstance();
            transformer.configure(transformerConfig);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public CljAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs) {
        schemaRegistry = client;
        configure(configs);
        inner = new KafkaAvroSerializer(client, configs);
    }

    public CljAvroSerializer(SchemaRegistryClient client, Map<String, ?> configs, KafkaAvroSerializer serializer) {
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
        return inner.serialize(topic, transformer.fromCljToAvro(schema, map));
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
