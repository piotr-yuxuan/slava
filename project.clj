(defproject slava "0.0.7-SNAPSHOT"
  :description "Infer specs from any Avro type"
  :url "https://cljdoc.org/d/com.slava/com.slava/0.0.7-SNAPSHOT"
  :license {:name "GNU GPL v3+"
            :url "http://www.gnu.org/licenses/gpl-3.0.en.html"
            :addendum "GPL_ADDITION.md"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.apache.avro/avro "1.9.1"]
                 [org.apache.kafka/kafka_2.12 "5.3.1-ce"] ;; bug: cannot access VerifiableProperties
                 [io.confluent/kafka-streams-avro-serde "5.3.2"]]
  :aot [com.slava.conversion-native]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["src/test/clojure"
               "src/test/java"]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.slf4j/slf4j-nop "2.0.0-alpha1"]]}
             :test {:java-source-paths ["src/main/java"
                                        "src/test/java"]
                    :dependencies [[org.clojure/test.check "0.10.0"]
                                   [org.clojure/spec.alpha "0.2.176"]
                                   [com.bakdata.fluent-kafka-streams-tests/schema-registry-mock "2.0.4"]
                                   [org.apache.kafka/kafka-streams-test-utils "5.3.1-ce"]
                                   [kovacnica/clojure.network.ip "0.1.3"]]}
             :precomp {:source-paths ["src/main/clojure"]
                       :aot [com.slava.conversion-native]}}
  :repositories [["confluent" "https://packages.confluent.io/maven/"]])
