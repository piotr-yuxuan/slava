(defproject piotr-yuxuan/slava (-> "./resources/slava.version" slurp .trim)
  :description "Kafka Avro Serde for Clojure"
  :url "https://github.com/piotr-yuxuan/slava"
  :license {:name "European Union Public License 1.2 or later"
            :url "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/slava"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :dependencies [[byte-streams/byte-streams "0.2.5-alpha2"]
                 [camel-snake-kebab/camel-snake-kebab "0.4.3"]
                 [com.github.piotr-yuxuan/slava-record "0.0.1"]
                 [potemkin/potemkin "0.4.7"]]
  :aot :all
  :profiles {:github {:github/topics ["clojure" "kafka" "avro" "schema-registry"
                                      "serdes" "serde" "confluent" "kafka-streams"
                                      "avro-kafka" "avro-schema-registry"]
                      :github/private? false}
             :provided {:dependencies [[org.clojure/clojure "1.12.0-beta1"]
                                       [io.confluent/kafka-avro-serializer "7.6.2"]
                                       [org.apache.avro/avro "1.11.3"]]}
             :dev {:global-vars {*warn-on-reflection* true}}
             :test {:dependencies [[com.bakdata.fluent-kafka-streams-tests/schema-registry-mock "2.14.0"]
                                   [org.apache.kafka/kafka-clients "7.6.2-ce"]
                                   [org.apache.kafka/kafka-streams-test-utils "7.6.2-ce"]]}
             :jar {:jvm-opts ["-Dclojure.compiler.disable-locals-clearing=false"
                              "-Dclojure.compiler.direct-linking=true"]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.91.1392"]]}]}
  :repositories [["confluent" {:url "https://packages.confluent.io/maven/"}]]
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/WALTER_CLOJARS_USERNAME
                                    :password :env/WALTER_CLOJARS_PASSWORD}]
                        ["github" {:sign-releases false
                                   :url "https://maven.pkg.github.com/piotr-yuxuan/slava"
                                   :username :env/GITHUB_ACTOR
                                   :password :env/WALTER_GITHUB_PASSWORD}]])
