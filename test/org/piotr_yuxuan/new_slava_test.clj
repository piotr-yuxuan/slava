(ns org.piotr-yuxuan.new-slava-test
  (:require [clojure.test :refer :all]
            [camel-snake-kebab.core :as csk]
            [org.piotr-yuxuan.new-slava :as slava])
  (:import (org.apache.avro SchemaBuilder)))

(deftest unit-tests
  (let [schema (-> (SchemaBuilder/builder)
                   (.record "Nested")
                   (.namespace "org.piotr-yuxuan.test")
                   .fields
                   (.name "simpleField") .type .stringType (.stringDefault "default value")
                   (.name "otherField") .type .stringType .noDefault
                   .endRecord)
        compiled (slava/compile-clj->avro {:enum-name csk/->SCREAMING_SNAKE_CASE_STRING
                                           :field-name csk/->kebab-case-keyword
                                           :nil-as-absent? true}
                                          schema)]
    (compiled {:simple-field "some-value", :other-field "some-value"})))
