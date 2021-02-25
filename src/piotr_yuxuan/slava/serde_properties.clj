(ns piotr-yuxuan.slava.serde-properties
  (:import (java.util Collections)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient CachedSchemaRegistryClient)
           (io.confluent.kafka.serializers AbstractKafkaSchemaSerDeConfig)
           (io.confluent.kafka.schemaregistry.testutil MockSchemaRegistry)
           (io.confluent.kafka.schemaregistry.avro AvroSchemaProvider)))

;; Shadow private code because we don't want to get rogue and break into internals.
;; See io.confluent.kafka.serializers/AbstractKafkaSchemaSerDe#configureClientProperties

(defn new-client
  ^SchemaRegistryClient [^AbstractKafkaSchemaSerDeConfig config]
  (let [urls (.getSchemaRegistryUrls config)
        providers (Collections/singletonList (AvroSchemaProvider.))]
    (if-let [mockScope (MockSchemaRegistry/validateAndMaybeGetMockScope urls)]
      (MockSchemaRegistry/getClientForScope mockScope providers)
      (CachedSchemaRegistryClient.
        urls
        (.getMaxSchemasPerSubject config)
        providers
        (.originalsWithPrefix config "")
        (.requestHeaders config)))))

(defn client-properties
  [{:keys [client]} ^AbstractKafkaSchemaSerDeConfig config isKey]
  {:client (or client (new-client config))
   :isKey isKey
   :key-subject-name-strategy (.keySubjectNameStrategy config)
   :value-subject-name-strategy (.keySubjectNameStrategy config)
   :use-schema-reflection (.keySubjectNameStrategy config)})
