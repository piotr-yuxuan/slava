(ns piotr-yuxuan.slava
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu]
            [camel-snake-kebab.core :as csk]
            [jsonista.core :as json])
  (:import (org.apache.avro Schema Schema$RecordSchema SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$FieldDefault Schema$Field Schema$Type SchemaBuilder$MapDefault SchemaBuilder$GenericDefault Schema$StringSchema SchemaBuilder$StringBldr)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder GenericData$EnumSymbol)
           (java.lang.reflect Method)
           (io.confluent.kafka.serializers KafkaAvroDeserializer)
           (java.util Map)))

(def avro-schema-transformer
  {:name :avro.schema
   :enter #(do (println :avro.schema :no/op) %)
   :leave #(do (println :in-overload :leave :map (type %)) %)})

(def avro-transformer
  (mt/transformer
    {:name :avro-overload
     :decoders {:map {:enter #(do (println :in-overload :enter :map (type %)) %)
                      :leave #(do (println :in-overload :leave :map (type %)) %)}
                'string? {:enter #(do (println :in-overload :enter :string? (type %)) %)
                          :leave #(do (println :in-overload :leave :string? (type %)) %)}
                'int? {:enter #(do (println :in-overload :enter :int? (type %)) %)
                       :leave #(do (println :in-overload :leave :int? (type %)) %)}}
     :encoders {:map {:enter #(do (println :in-overload :enter :map (type %)) %)
                      :leave #(do (println :in-overload :leave :map (type %)) %)}
                'string? {:enter #(do (println :in-overload :enter :string? (type %)) %)
                          :leave #(do (println :in-overload :leave :string? (type %)) %)}
                'int? {:enter #(do (println :in-overload :enter :int? (type %)) %)
                       :leave #(do (println :in-overload :leave :int? (type %)) %)}}}
    {:name :avro
     :decoders {:map {:leave json/read-value}
                'string? {:enter identity}
                'int? {:enter #(Integer/parseInt %)}}
     :encoders {:map {:leave json/write-value-as-string}
                'string? {:enter identity}
                'int? {:enter str}}}))

(def testSchema
  [:map
   [:some-string string?]
   [:some-int int?]
   [:some-map [:map
               [:some-string string?]
               [:some-int int?]]]])

(def testInstance
  {:some-string "asdiu"
   :some-int 2
   :some-map {:some-string "asdiu"
              :some-int 2}})

(m/encode testSchema
          testInstance
          avro-transformer)

(m/encode testSchema
          testInstance
          avro-transformer)

(def ^Schema$RecordSchema nested-schema
  (-> (SchemaBuilder/builder)
      (.record "Nested")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "simpleField") .type .stringType (.stringDefault "default value")
      (.name "otherField") .type .stringType .noDefault
      ^SchemaBuilder$FieldDefault .endRecord))

(clojure.walk/keywordize-keys (json/read-value (str nested-schema)))

(def ^Schema$RecordSchema wrapping-schema
  (-> (SchemaBuilder/builder)
      (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "utf8Field") .type .stringBuilder ^SchemaBuilder$StringBldr (.prop "avro.java.string" "Utf8") ^SchemaBuilder$FieldDefault .endString .noDefault
      (.name "intField") .type .intType .noDefault
      (.name "doubleField") .type .doubleType (.doubleDefault (double 12))
      (.name "mapField") .type .map .values ^SchemaBuilder$MapDefault .stringType (.mapDefault {"default singleton map key" "🚀"})
      (.name "nestedField") ^SchemaBuilder$GenericDefault (.type nested-schema) .noDefault
      .endRecord))

(clojure.walk/keywordize-keys (json/read-value (str wrapping-schema)))


(def AvroRecord
  [:and map?])

(def AvroEnum
  [:and any?])

(def AvroArray
  [:and sequential?])

(def AvroMap
  [:map-of string? any?])

(def AvroUnion
  [:and any?])

(def AvroFixed
  [:and any?])

(def AvroString
  [:and string?])

(def AvroBytes
  [:and bytes?])

(def AvroInt
  [:and int?])

(def AvroLong
  [:fn #(instance? Long %)])

(def AvroFloat
  [:and float?])

(def AvroDouble
  [:and double?])

(def AvroBoolean
  [:and boolean?])

(def AvroNull
  [:and nil?])

(map #(.getType (.schema ^Schema$Field %)) (.getFields ^Schema$RecordSchema wrapping-schema))

(def testSchema
  [:map
   [:some-string string?]
   [:some-int int?]])

(def testInstance
  {:some-string "asdiu"
   :some-int 2})

(def schema [:fn {:decode/date #(do (println "java.time.ZonedDateTime-unified decode" (type %))
                                    (java.time.ZonedDateTime/parse %))
                  :encode/date #(do (println "ZonedDateTime-unified encode" (type %))
                                    (str %))}
             #(do (println "ZonedDateTime-unified valid?" (type %))
                  (instance? java.time.ZonedDateTime %))])

#_(as-> {:zoned-date-time "2020-05-27T16:14:29.375917+01:00[Europe/Berlin]"} data
        (m/decode [:map [:zoned-date-time schema]] data (mt/transformer {:name :date}))
        (do (println :valid? (m/validate [:map [:zoned-date-time schema]] data)) data)
        (m/encode [:map [:zoned-date-time schema]] data (mt/transformer {:name :date})))


(defn deserialize
  ;; Is GenericData$Record the correct apex type?
  ^Map [^Schema schema ^GenericData$Record record]
  record)

(defn serialize
  ^Map [^Schema schema ^Map m]
  m)
