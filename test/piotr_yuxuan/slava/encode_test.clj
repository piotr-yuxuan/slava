(ns piotr-yuxuan.slava.encode-test
  (:require [clojure.test :refer [deftest testing are is]]
            [piotr-yuxuan.slava.encode :as encode]
            [piotr-yuxuan.slava.config :as config])
  (:import (org.apache.avro SchemaBuilder SchemaBuilder$NamespacedBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$UnionAccumulator)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder)))

(deftest encoder-name-test
  (are [x y] (= x y)
    :encoder/avro-record (encode/encoder-name (-> (SchemaBuilder/builder) (.record "Record") .fields .endRecord))
    :encoder/avro-array (encode/encoder-name (-> (SchemaBuilder/builder) .array .items .stringType))
    :encoder/avro-map (encode/encoder-name (-> (SchemaBuilder/builder) .map .values .stringType))
    :encoder/avro-long (encode/encoder-name (-> (SchemaBuilder/builder) .longType))))

(deftest avro-record-test
  (let [nested-record-schema (-> (SchemaBuilder/builder)
                                 ^SchemaBuilder$NamespacedBuilder (.record "NestedRecord")
                                 ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.old_test")
                                 ^SchemaBuilder$FieldAssembler .fields
                                 (.name "field") .type .intType .noDefault
                                 (.name "mapField") (.type (-> (SchemaBuilder/builder) .map .values .intType)) .noDefault
                                 ^GenericData$Record .endRecord)
        record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "Record")
                          ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.old_test")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .intType .noDefault
                          (.name "nestedRecord") (.type nested-record-schema) .noDefault
                          ^GenericData$Record .endRecord)
        record-encoder (encode/avro-record config/default record-schema)]
    (testing "record is properly encoded, as are field values"
      (is (= (record-encoder {"field" (int 1)
                              "nestedRecord" {"field" (int 1)
                                              "mapField" {"field" (int 1)}}})
             (.build (doto (GenericRecordBuilder. record-schema)
                       (.set "field" (int 1))
                       (.set "nestedRecord" (.build (doto (GenericRecordBuilder. nested-record-schema)
                                                      (.set "field" (int 1))
                                                      (.set "mapField" {"field" (int 1)})))))))))))

(deftest avro-array-test
  (let [map-schema (-> (SchemaBuilder/builder) .map .values .longType)
        array-schema (-> (SchemaBuilder/builder) .array (.items map-schema))
        array-encoder (encode/avro-array config/default array-schema)]
    (testing "no need to mount an encoder"
      (is (not array-encoder))))
  (let [record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .longType .noDefault
                          ^GenericData$Record .endRecord)
        map-schema (-> (SchemaBuilder/builder) .map (.values record-schema))
        array-schema (-> (SchemaBuilder/builder) .array (.items map-schema))
        array-encoder (encode/avro-array config/default array-schema)]
    (testing "encoder needs to be mounted"
      (is array-encoder))
    (testing "array is properly encoded, as are items values"
      (let [record (.build (doto (GenericRecordBuilder. record-schema)
                             (.set "field" 1)))]
        (is (= (array-encoder [{"field" {"field" 1}} {"field" {"field" 1}}])
               [{"field" record} {"field" record}]))))))

(deftest avro-map-test
  (let [record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .longType .noDefault
                          ^GenericData$Record .endRecord)
        map-schema (-> (SchemaBuilder/builder) .map (.values (-> (SchemaBuilder/builder) .map (.values record-schema))))
        map-encoder (encode/avro-map config/default map-schema)
        record (.build (doto (GenericRecordBuilder. record-schema)
                         (.set "field" 1)))]
    (testing "map is properly encoded, as are its values"
      (is (= (map-encoder {"field" {"field" {"field" 1}}})
             {"field" {"field" record}})))))

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
    (testing "No union encoder created, as per the default config values"
      (is (not (encode/avro-union config/default union-schema)))))
  (let [record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .longType .noDefault
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
                         .and (.type map-schema) ; map is before record
                         .and (.type record-schema)
                         .endUnion)
        union-encoder (encode/avro-union config/default union-schema)
        record (.build (doto (GenericRecordBuilder. record-schema)
                         (.set "field" 1)))]
    (testing "encoder is created for concrete container type"
      (is union-encoder))
    (is (= nil (union-encoder nil)))
    (is (= [] (union-encoder [])))
    (is (= [nil] (union-encoder [nil])))
    (is (= record (union-encoder {"field" 1 :piotr-yuxuan.slava/type :avro-record})))
    (is (= [nil record {"field" record}]
           (union-encoder [nil
                           (with-meta {"field" 1} {:piotr-yuxuan.slava/type :avro-record})
                           {"field" {"field" 1}}])
           (union-encoder [nil
                           {"field" 1
                            :piotr-yuxuan.slava/type :avro-record}
                           {"field" {"field" 1}}])))
    (is (= [{"field" record}] (union-encoder [{"field" {"field" 1}}])))
    (is (= [record] (union-encoder [{"field" 1 :piotr-yuxuan.slava/type :avro-record}])))
    (is (= {"field" record}) (union-encoder {"field" {"field" 1}})))
  (testing "resolve tie when a union contains map, and then record"
    (let [record-schema (-> (SchemaBuilder/builder)
                            ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                            ^SchemaBuilder$FieldAssembler .fields
                            (.name "field") .type .longType .noDefault
                            ^GenericData$Record .endRecord)
          map-schema (-> (SchemaBuilder/builder) .map .values .longType)
          union-schema (-> (SchemaBuilder/builder)
                           .unionOf
                           ^SchemaBuilder$UnionAccumulator (.type map-schema) .and (.type record-schema)
                           .endUnion)
          union-encoder (encode/avro-union config/default union-schema)
          record (.build (doto (GenericRecordBuilder. record-schema)
                           (.set "field" 1)))]
      (is (= record (union-encoder {"field" 1 :piotr-yuxuan.slava/type :avro-record})))
      (is (= record (union-encoder (with-meta {"field" 1} {:piotr-yuxuan.slava/type :avro-record}))))
      (is (= {"field" 1}) (union-encoder {"field" 1}))))
  (testing "resolve tie when a union contains record, and then map"
    (let [record-schema (-> (SchemaBuilder/builder)
                            ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
                            ^SchemaBuilder$FieldAssembler .fields
                            (.name "field") .type .longType .noDefault
                            ^GenericData$Record .endRecord)
          map-schema (-> (SchemaBuilder/builder) .map .values .longType)
          union-schema (-> (SchemaBuilder/builder)
                           .unionOf
                           ^SchemaBuilder$UnionAccumulator (.type record-schema) .and (.type map-schema)
                           .endUnion)
          union-encoder (encode/avro-union config/default union-schema)
          record (.build (doto (GenericRecordBuilder. record-schema)
                           (.set "field" 1)))]
      (is (= record (union-encoder {"field" 1})))
      (is (= {"field" 1}) (union-encoder (with-meta {"field" 1} {:piotr-yuxuan.slava/type :avro-map}))))))
