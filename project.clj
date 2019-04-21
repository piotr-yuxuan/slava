(defproject slava "0.0.7-SNAPSHOT"
  :description "Infer specs from any Avro type"
  :url "https://cljdoc.org/d/com.slava/com.slava/0.0.7-SNAPSHOT"
  :license {:name "GNU GPL v3+"
            :url "http://www.gnu.org/licenses/gpl-3.0.en.html"
            :addendum "GPL_ADDITION.md"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.apache.avro/avro "1.8.2"]
                 [camel-snake-kebab "0.4.0"]
                 [org.clojure/test.check "0.10.0-alpha4"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [clj-time "0.15.1"]
                 [org.slf4j/slf4j-nop "1.7.22"]
                 [io.confluent/kafka-streams-avro-serde "5.2.1"]]
  :main com.slava.core
  :path "src"
  :aot [com.slava.GenericAvroDeserializer
        com.slava.GenericAvroSerializer
        com.slava.SpecificAvroDeserializer
        com.slava.SpecificAvroSerializer]
  :profiles {:uberjar {:aot :all}
             :pom {:dependencies [[io.confluent/kafka-schema-registry-maven-plugin "5.1.2"]]}
             :test {:java-source-paths ["target/generated-sources"]
                    :dependencies [[org.apache.kafka/kafka-streams-test-utils "2.2.0"]]}
             :dev {:java-source-paths ["target/generated-sources"]
                   :dependencies [[org.apache.kafka/kafka-streams-test-utils "2.2.0"]]}}
  :repositories [["confluent" "https://packages.confluent.io/maven/"]])
