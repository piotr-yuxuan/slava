(ns piotr-yuxuan.slava.schema-registry
  "FIXME add cljdoc"
  (:import (io.confluent.kafka.schemaregistry.avro AvroSchemaProvider)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient CachedSchemaRegistryClient)
           (io.confluent.kafka.schemaregistry.testutil MockSchemaRegistry)
           (io.confluent.kafka.serializers AbstractKafkaSchemaSerDeConfig)
           (java.util Collections)))

;; Shadow private code because we don't want to get rogue and break into internals.
;; See io.confluent.kafka.serializers/AbstractKafkaSchemaSerDe#configureClientProperties

(defn new-client
  "FIXME add cljdoc"
  ^SchemaRegistryClient [^AbstractKafkaSchemaSerDeConfig config]
  (let [urls (.getSchemaRegistryUrls config)
        providers (Collections/singletonList (AvroSchemaProvider.))]
    (if-let [mockScope (MockSchemaRegistry/validateAndMaybeGetMockScope urls)]
      (MockSchemaRegistry/getClientForScope mockScope providers)
      (CachedSchemaRegistryClient.
        urls
        (.getMaxSchemasPerSubject config)
        providers
        (.originalsWithPrefix config "FIXME add cljdoc")
        (.requestHeaders config)))))

(def config-keys
  "FIXME add cljdoc"
  (set (.names (AbstractKafkaSchemaSerDeConfig/baseConfigDef))))

(defn config
  "FIXME add cljdoc"
  [{:keys [client]} ^AbstractKafkaSchemaSerDeConfig config isKey]
  {:client (or client (new-client config))
   :isKey isKey
   :key-subject-name-strategy (.keySubjectNameStrategy config)
   :value-subject-name-strategy (.valueSubjectNameStrategy config)
   :use-schema-reflection (.useSchemaReflection config)})