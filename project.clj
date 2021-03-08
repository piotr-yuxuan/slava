(defproject piotr-yuxuan/slava (-> "./resources/slava.version" slurp .trim)
  :description "Kafka Avro Serde for Clojure"
  :url "https://github.com/piotr-yuxuan/slava"
  :license {:name "GNU General Public License v3.0 or later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"
            :distribution :repo
            :comments "See also GPL_ADDITION.org"}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/slava.git"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]}
  :resolve-aliases [:test]
  :repositories [["confluent" "https://packages.confluent.io/maven/"]])
