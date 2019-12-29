# 🇷🇺 [slava](https://clojars.org/slava)

[![Clojars Project](https://img.shields.io/clojars/v/com.slava.svg)](https://clojars.org/com.slava)

[![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

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

![слава советскому народу](resources/слава-советскому-народу.jpg)

This poster reads: « Glory to Soviet People, creator of a powerful
aviation ». Created in 1954 year it is a perfect symbol of the
powerful soviet militarism stream of that time. Avro was a British
aircraft manufacturer, but as the propaganda goes, _Soviet aircrafts
are the best aircrafts_.

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
            [clojure.network.ip :as ip]))

(declare properties schema-registry schema-registry-client ip-array-input-topic ip-v4-output-topic)

(def ^Schema ip-v4-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv4") (.size 4)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v4-schema) [this schema ^GenericData$Fixed data] …)
(defmethod serde/to-avro-schema-name (.getFullName ip-v4-schema) [_ schema ^IPAddress ip-adress] …)

(def ^Schema ip-v6-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv6") (.size 16)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v6-schema) [this schema ^GenericData$Fixed data] …)
(defmethod serde/to-avro-schema-name (.getFullName ip-v6-schema) [_ schema ^IPAddress ip-adress] …)

(def ^Schema ip-array-input-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Input")
      .fields
      (.name "array") .type .array .items .unionOf (.type ip-v4-schema) .and (.type ip-v6-schema) .endUnion .noDefault
      .endRecord))

(def ^Schema ip-v4-output-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Output")
      .fields
      (.name "address") (.type ip-v4-schema) .noDefault
      .endRecord))

(def topology
  (let [builder (StreamsBuilder.)]
    (-> (.stream builder ip-array-input-topic)
        (.flatMapValues (reify ValueMapper
                          (apply [_ record]
                            (map #(do {::address %})
                                 (record :com.slava.readme-test.Input/array)))))
        (.filter (reify Predicate
                   (test [_ uuid record]
                     (->> ^IPAddress (record ::address)
                          (.version)
                          (= 4)))))
        (.mapValues (reify ValueMapper
                      (apply [_ record]
                        (clojure.set/rename-keys record
                                                 {::address :com.slava.readme-test.Output/address}))))
        (.to ip-v4-output-topic))
    (.build builder)))

(deftest kafka-streams-integration-test
  (with-open [^TopologyTestDriver test-driver (TopologyTestDriver. topology properties)]
    (doseq [record (list {:com.slava.readme-test.Input/array [(ip/make-ip-address "192.168.1.1")
                                                              (ip/make-ip-address "1::1")]}
                         {:com.slava.readme-test.Input/array [(ip/make-ip-address "1::2")
                                                              (ip/make-ip-address "1::3")]}
                         {:com.slava.readme-test.Input/array [(ip/make-ip-address "192.168.1.2")
                                                              (ip/make-ip-address "192.168.1.3")]})]
      (.pipeInput test-driver [(.create consumer-record-factory ip-array-input-topic (UUID/randomUUID) record)]))
 ✅ (is (= (for [^ProducerRecord record (take-while some? (repeatedly #(.readOutput test-driver ip-v4-output-topic)))]
             (.deserialize (.deserializer value-avro-serde) ip-v4-output-topic (.value ^ProducerRecord record)))
           (list {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.1")}
                 {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.2")}
                 {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.3")})))))


```

Further documentation in available in [![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)


# Related projects

Here are other projects. They are quite awesome. Perhaps they would be
more useful to you than slava.

- https://github.com/damballa/abracad
- https://github.com/komolovf/kfk-avro-bridge
- https://github.com/ovotech/kafka-avro-confluent
- https://github.com/deercreeklabs/lancaster
