(defproject org.piotr-yuxuan/slava "0.1.0"
  :description "Infer specs from any Avro type"
  :url "https://cljdoc.org/d/com.slava/com.slava/0.0.24"
  :license {:name "GNU GPL v3+"
            :url "http://www.gnu.org/licenses/gpl-3.0.en.html"
            :addendum "GPL_ADDITION.md"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.apache.avro/avro "1.9.2"]
                 [io.confluent/kafka-avro-serializer "5.4.1"]
                 [camel-snake-kebab "0.4.1"]]
  :aot [org.piotr-yuxuan.slava]
  :source-paths ["src"]
  :resource-paths ["resources"]
  :java-source-paths ["src"]
  :test-paths ["test"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :global-vars {*warn-on-reflection* true}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner" "--watch"]}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.slf4j/slf4j-nop "2.0.0-alpha1"]]
                   :source-paths ["dev"]
                   :resource-paths ["dev-resources"]}
             :test {:java-source-paths ["src/main/java"
                                        "src/test/java"]
                    :dependencies [[io.confluent/kafka-streams-avro-serde "5.4.1"]
                                   [org.clojure/test.check "1.0.0"]
                                   [org.clojure/spec.alpha "0.2.187"]
                                   [com.bakdata.fluent-kafka-streams-tests/schema-registry-mock "2.1.0"]
                                   [org.apache.kafka/kafka-streams-test-utils "5.3.1-ce"]
                                   [kovacnica/clojure.network.ip "0.1.3"]]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.0.632"]]}]
             :precomp {:source-paths ["src/main/clojure"]
                       :aot [org.piotr-yuxuan.clj<->avro]}}
  :repositories [["confluent" "https://packages.confluent.io/maven/"]])
