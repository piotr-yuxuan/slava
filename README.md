# 🇷🇺 [slava](https://clojars.org/slava)

[![Clojars Project](https://img.shields.io/clojars/v/com.slava.svg)](https://clojars.org/com.slava)

[![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

![слава советскому народу](resources/слава-советскому-народу.jpg)

This poster reads: « Glory to Soviet People, creator of a powerful
aviation ». Created in 1954 year it is a perfect symbol of the
powerful soviet militarism stream of that time. Avro was a British
aircraft manufacturer, but as the propaganda goes, _Soviet aircrafts
are the best aircrafts_.

# What does it aim at?

Present Kafka messages as primitive map-like data structures, which
are more friendly than Avro specific / generic record objects. Achieve
it by providing an opinionated Avro Kafka Serde heavily relying on
upstream Avro and Confluent code.

- Very easy customisation: create your own tailored Serde
- Implement a custom `org.apache.kafka.common.serialization.Serde`
- Work on the JVM, useful for Clojure or any other language
- Rely as much as possible on upstream Avro and Confluent codes
- Well-integrated with Confluent schema registry

# Test example with Kafka Streams

See full [test
file](https://github.com/piotr-yuxuan/slava/blob/master/src/test/clojure/com/slava/readme_test.clj)
for details such as import and explicit vars not defined in this
snippet. The full test actually passes 🤗

The following snippet how to serialise from and to Avro a new custom
data type: IP address. It also demonstrates how to seamlessly
manipulate Clojure datatypes within Kafka Streams and how to integrate
custom Serde in your test code.

``` clojure
(ns com.slava.readme-test
  (:require [clojure.test :refer :all]
            [com.slava.conversion-native :as serde]
            [clojure.network.ip :as ip])
  (:import …))

(def ip-v4-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv4") (.size 4)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v4-schema) [this schema ^GenericData$Fixed data] (ip/make-ip-address (.array (serde/from-avro-schema-type this schema data))))
(defmethod serde/to-avro-schema-name (.getFullName ip-v4-schema) [_ schema ^IPAddress ip-adress] (GenericData$Fixed. schema (.array (doto (ByteBuffer/allocate 4) (.putInt (.numeric_value ip-adress)) (.rewind)))))

(def ip-v6-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv6") (.size 16)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v6-schema) [this schema ^GenericData$Fixed data] (ip/make-ip-address (.array (serde/from-avro-schema-type this schema data))))
(defmethod serde/to-avro-schema-name (.getFullName ip-v6-schema) [_ schema ^IPAddress ip-adress] (GenericData$Fixed. schema (.array (doto (ByteBuffer/allocate 16) (.putDouble (.numeric_value ip-adress)) (.rewind)))))

(def ^String ip-array-input-topic "ip-array-input-topic")
(def ^String ip-v4-output-topic "ip-v4-output-topic")

(def ^Schema ip-array-input-schema (-> (SchemaBuilder/builder (str *ns*)) (.record "input") .fields (.name "array") .type .array .items .unionOf (.type ip-v4-schema) .and (.type ip-v6-schema) .endUnion .noDefault .endRecord))
(def ^Schema ip-v4-output-schema (-> (SchemaBuilder/builder (str *ns*)) (.record "output") .fields (.name "address") (.type ip-v4-schema) .noDefault .endRecord))

(def topology
  (let [builder (StreamsBuilder.)]
    (-> (.stream builder ip-array-input-topic)
        (.flatMapValues (reify ValueMapper (apply [_ v] (map #(do {"address" %}) (get v "array")))))
        (.filter (reify Predicate (test [_ k v] (= 4 (.version ^IPAddress (get v "address"))))))
        (.to ip-v4-output-topic))
    (.build builder)))

(def key-avro-serde (doto (Serdes$UUIDSerde.) (.configure properties (boolean :key))))
(def value-avro-serde (doto (NativeAvroSerde. schema-registry-client) (.configure properties (boolean (not :key)))))
(def consumer-record-factory (ConsumerRecordFactory. (.serializer key-avro-serde) (.serializer value-avro-serde)))

(deftest kafka-streams-integration-test
  (with-open [^TopologyTestDriver test-driver (TopologyTestDriver. topology properties)]
    (doseq [record (list {"array" [(ip/make-ip-address "192.168.1.1")
                                   (ip/make-ip-address "1::1")]}
                         {"array" [(ip/make-ip-address "1::2")
                                   (ip/make-ip-address "1::3")]}
                         {"array" [(ip/make-ip-address "192.168.1.2")
                                   (ip/make-ip-address "192.168.1.3")]})]
      (.pipeInput test-driver [(.create consumer-record-factory ip-array-input-topic (UUID/randomUUID) record)]))
    (is (= (for [^ProducerRecord record (take-while some? (repeatedly #(.readOutput test-driver ip-v4-output-topic)))]
             (.deserialize (.deserializer value-avro-serde) ip-v4-output-topic (.value ^ProducerRecord record)))
           (list {"address" (ip/make-ip-address "192.168.1.1")}
                 {"address" (ip/make-ip-address "192.168.1.2")}
                 {"address" (ip/make-ip-address "192.168.1.3")})))))
```

Further documentation in available in [![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)


# Related projects

Here are other projects. They are quite awesome. Perhaps they would be
more useful to you than slava.

- https://github.com/damballa/abracad
- https://github.com/komolovf/kfk-avro-bridge
- https://github.com/ovotech/kafka-avro-confluent
- https://github.com/deercreeklabs/lancaster
