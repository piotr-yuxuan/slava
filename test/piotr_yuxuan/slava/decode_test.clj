(ns piotr-yuxuan.slava.decode-test
  (:require [piotr-yuxuan.slava.config :as config]
            [piotr-yuxuan.slava.decode :as decode]
            [clojure.test :refer [deftest testing are is]])
  (:import (piotr_yuxuan.slava.slava_record SlavaGenericRecord)
           (org.apache.avro SchemaBuilder Schema LogicalTypes Schema$Type SchemaBuilder$NamespacedBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$UnionAccumulator)
           (org.apache.avro.generic GenericRecordBuilder GenericData$Record)))

(deftest decoder-name-test
  (are [x y] (= x y)
    :decoder/avro-record (decode/decoder-name (-> (SchemaBuilder/builder) (.record "Record") .fields .endRecord))
    :decoder/avro-array (decode/decoder-name (-> (SchemaBuilder/builder) .array .items .stringType))
    :decoder/avro-map (decode/decoder-name (-> (SchemaBuilder/builder) .map .values .stringType))
    :decoder/avro-long (decode/decoder-name (.longType (SchemaBuilder/builder)))
    :decoder/avro-long (decode/decoder-name (.addToSchema (LogicalTypes/timestampMillis) (Schema/create Schema$Type/LONG)))))

(deftest avro-record-test
  (let [nested-record-schema (-> (SchemaBuilder/builder)
                                 ^SchemaBuilder$NamespacedBuilder (.record "NestedRecord")
                                 ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
                                 ^SchemaBuilder$FieldAssembler .fields
                                 (.name "field") .type .intType .noDefault
                                 (.name "mapField") (.type (-> (SchemaBuilder/builder) .map .values .intType)) .noDefault
                                 ^GenericData$Record .endRecord)
        record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "Record")
                          ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .intType .noDefault
                          (.name "nestedRecord") (.type nested-record-schema) .noDefault
                          ^GenericData$Record .endRecord)
        record-decoder (decode/avro-record config/default record-schema)
        record-value (.build (doto (GenericRecordBuilder. record-schema)
                               (.set "field" (int 1))
                               (.set "nestedRecord" (.build (doto (GenericRecordBuilder. nested-record-schema)
                                                              (.set "field" (int 1))
                                                              (.set "mapField" {"field" 1}))))))]
    (testing "record is properly decoded, as are field values"
      (is (instance? SlavaGenericRecord (record-decoder record-value)))
      (is (= (record-decoder record-value)
             {"field" 1
              "nestedRecord" {"field" 1
                              "mapField" {"field" 1}}})))))

(deftest avro-array-test
  (let [map-schema (-> (SchemaBuilder/builder) .map .values .longType)
        array-schema (-> (SchemaBuilder/builder) .array (.items map-schema))
        array-decoder (decode/avro-array config/default array-schema)]
    (testing "array is properly decoded, as are items values"
      (is (= (array-decoder [[{"field" 1} {"field" 2}] [{"field" 3}] []])
             [[{"field" 1} {"field" 2}] [{"field" 3}] []])))))

(deftest avro-map-test
  (let [record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .longType .noDefault
                          ^GenericData$Record .endRecord)
        map-schema (-> (SchemaBuilder/builder) .map (.values (-> (SchemaBuilder/builder) .map (.values record-schema))))
        map-decoder (decode/avro-map config/default map-schema)
        record (.build (doto (GenericRecordBuilder. record-schema)
                         (.set "field" 1)))]
    (testing "map is properly decoded, as are its values"
      (is (= (map-decoder {"field" {"field" record}})
             {"field" {"field" {"field" 1}}}))))
  (testing "opinionated config on field-name"
    (let [map-schema (-> (SchemaBuilder/builder) .map .values .longType)
          record-schema (-> (SchemaBuilder/builder)
                            ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                            ^SchemaBuilder$FieldAssembler .fields
                            (.name "prefix") (.type map-schema) .noDefault
                            ^GenericData$Record .endRecord)
          union-decoder (decode/avro-record config/opinionated record-schema)
          record (.build (doto (GenericRecordBuilder. record-schema)
                           (.set "prefix" {"map-entry" 1})))]
      (is (= {:prefix #:prefix{:map-entry 1}} (union-decoder record)))
      (is (instance? SlavaGenericRecord (union-decoder record))))))

(deftest avro-union-test
  (let [;; All concrete, non-container types
        union-schema (-> (SchemaBuilder/builder)
                         .unionOf
                         ^SchemaBuilder$UnionAccumulator .nullType
                         .and (.type (-> (SchemaBuilder/builder) (.enumeration "enum") (.symbols (into-array String ["A" "B" "C"]))))
                         .and .stringType
                         .and (.type (-> (SchemaBuilder/builder) (.fixed "fixed") (.size 16)))
                         .and .bytesType
                         .and .intType
                         .and .longType
                         .and .floatType
                         .and .doubleType
                         .and .booleanType
                         .endUnion)]
    (testing "No union decoder created, as per the default config values"
      (is (not (decode/avro-union config/default union-schema)))))
  (let [record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .intType .noDefault
                          ^GenericData$Record .endRecord)
        map-schema (-> (SchemaBuilder/builder) .map (.values record-schema))
        array-schema (-> (SchemaBuilder/builder) .array (.items (-> (SchemaBuilder/builder)
                                                                    .unionOf
                                                                    ^SchemaBuilder$UnionAccumulator .nullType
                                                                    .and (.type map-schema)
                                                                    .and (.type record-schema)
                                                                    .endUnion)))
        union-schema (-> (SchemaBuilder/builder)
                         .unionOf
                         ^SchemaBuilder$UnionAccumulator .nullType
                         .and (.type array-schema)
                         .and (.type map-schema)
                         .and (.type record-schema)
                         .endUnion)
        union-decoder (decode/avro-union config/default union-schema)
        record (.build (doto (GenericRecordBuilder. record-schema)
                         (.set "field" (int 1))))]
    (testing "decoder is created for concrete container type"
      (is union-decoder))
    (is (not (union-decoder nil)))
    (is (= [] (union-decoder [])))
    (is (= [nil] (union-decoder [nil])))
    (is (= [nil {"field" 1} {"field" {"field" 1}}] (union-decoder [nil record {"field" record}])))
    (is (= [{"field" {"field" 1}} {"field" 1} nil] (union-decoder [{"field" record} record nil])))
    (is (= {"field" {"field" 1}} (union-decoder {"field" record})))))
