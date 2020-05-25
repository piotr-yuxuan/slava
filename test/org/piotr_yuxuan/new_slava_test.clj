(ns org.piotr-yuxuan.new-slava-test
  (:require [clojure.test :refer :all]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro SchemaBuilder)))

{:enum-name csk/->SCREAMING_SNAKE_CASE_STRING
 :field-name #{} csk/->kebab-case-keyword
 :nil-as-absent? #{true false}
 :object-type #{:record :enum :array :map :union :fixed :string :bytes :int :long :float :double :boolean :null}}

(deftest unit-tests
  (let [schema (-> (SchemaBuilder/builder)
                   (.record "Nested")
                   (.namespace "org.piotr-yuxuan.test")
                   .fields
                   (.name "simpleField") .type .stringType (.stringDefault "default value")
                   (.name "otherField") .type .stringType .noDefault
                   .endRecord)]
    (compile-clj->avro)))
