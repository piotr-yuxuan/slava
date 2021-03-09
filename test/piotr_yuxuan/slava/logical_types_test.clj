(ns piotr-yuxuan.slava.logical-types-test
  (:require [clojure.test :refer :all]
            [piotr-yuxuan.slava.config :as config]
            [piotr-yuxuan.slava.logical-types :as logical-types])
  (:import (org.apache.avro LogicalTypes Schema Schema$Type)
           (org.apache.avro.generic GenericData$Fixed)
           (java.nio ByteBuffer)
           (java.time LocalDate LocalTime Instant LocalDateTime)))

(deftest logical-conversion-test
  (testing "decimal"
    (let [config {}
          schema (.addToSchema (LogicalTypes/decimal 9 3) (Schema/createFixed "fixed" nil "piotr_yuxuan.slava" 4))
          decoder ((config/conversion-coder .fromFixed) config schema)
          encoder ((config/conversion-coder .toFixed) config schema)
          ;; Warning: non-ascii characters as padding for fixed size
          data (GenericData$Fixed. schema (byte-streams/to-byte-array "   a"))]
      (are [x f] (= (f data) x)
        0.097M decoder
        data (comp encoder decoder))))
  (testing "duration"
    (let [config {}
          schema logical-types/duration-schema
          decoder ((config/conversion-coder .fromFixed) config schema)
          encoder ((config/conversion-coder .toFixed) config schema)
          data (->> [1 0 0 0 ; months, little-endian
                     0 1 0 0 ; days, little-endian
                     0 0 1 0 ; milliseconds, little-endian
                     ]
                    (map byte)
                    byte-array
                    (GenericData$Fixed. schema))]
      (are [x f] (= (f data) x)
        {:months 1, :days 256, :milliseconds 65536} decoder
        data (comp encoder decoder))))
  (testing "uuid"
    (let [config {}
          schema (.addToSchema (LogicalTypes/uuid) (Schema/create Schema$Type/STRING))
          decoder ((config/conversion-coder .fromCharSequence) config schema)
          encoder ((config/conversion-coder .toCharSequence) config schema)
          data "c1099e80-935a-4485-984a-cd71f52cd44c"]
      (is (-> data decoder (= #uuid"c1099e80-935a-4485-984a-cd71f52cd44c")))
      (is (-> data decoder encoder (= data)))))
  (testing "decimal"
    (let [config {}
          schema (.addToSchema (LogicalTypes/decimal 12 5) (Schema/create Schema$Type/BYTES))
          decoder ((config/conversion-coder .fromBytes) config schema)
          encoder ((config/conversion-coder .toBytes) config schema)
          data (byte-streams/convert "a" ByteBuffer)]
      (are [x f] (= (f data) x)
        0.00097M decoder
        data (comp encoder decoder))))
  (testing "date"
    (let [config {}
          schema (.addToSchema (LogicalTypes/date) (Schema/create Schema$Type/INT))
          decoder ((config/conversion-coder .fromInt) config schema)
          encoder ((config/conversion-coder .toInt) config schema)
          data (int 42)]
      (are [x f] (= (f data) x)
        (LocalDate/parse "1970-02-12") decoder
        data (comp encoder decoder))))
  (testing "time-millis"
    (let [config {}
          schema (.addToSchema (LogicalTypes/timeMillis) (Schema/create Schema$Type/INT))
          decoder ((config/conversion-coder .fromInt) config schema)
          encoder ((config/conversion-coder .toInt) config schema)
          data (int 1337)]
      (are [x f] (= (f data) x)
        (LocalTime/parse "00:00:01.337") decoder
        data (comp encoder decoder))))
  (testing "time-micros"
    (let [config {}
          schema (.addToSchema (LogicalTypes/timeMicros) (Schema/create Schema$Type/LONG))
          decoder ((config/conversion-coder .fromLong) config schema)
          encoder ((config/conversion-coder .toLong) config schema)
          data 1337234567]
      (are [x f] (= (f data) x)
        (LocalTime/parse "00:22:17.234567") decoder
        data (comp encoder decoder))))
  (testing "timestamp-millis"
    (let [config {}
          schema (.addToSchema (LogicalTypes/timestampMillis) (Schema/create Schema$Type/LONG))
          decoder ((config/conversion-coder .fromLong) config schema)
          encoder ((config/conversion-coder .toLong) config schema)
          data 1234567890123456789]
      (are [x f] (= (f data) x)
        (Instant/parse "+39123869-01-14T18:17:36.789Z") decoder
        data (comp encoder decoder))))
  (testing "timestamp-micros"
    (let [config {}
          schema (.addToSchema (LogicalTypes/timestampMicros) (Schema/create Schema$Type/LONG))
          decoder ((config/conversion-coder .fromLong) config schema)
          encoder ((config/conversion-coder .toLong) config schema)
          data Long/MAX_VALUE]
      (are [x f] (= (f data) x)
        (Instant/parse "+294247-01-10T04:00:54.775807Z") decoder
        data (comp encoder decoder))))
  (testing "local-timestamp-millis"
    (let [config {}
          schema (.addToSchema (LogicalTypes/localTimestampMillis) (Schema/create Schema$Type/LONG))
          decoder ((config/conversion-coder .fromLong) config schema)
          encoder ((config/conversion-coder .toLong) config schema)
          data Long/MAX_VALUE]
      (are [x f] (= (f data) x)
        (LocalDateTime/parse "+292278994-08-17T07:12:55.807") decoder
        data (comp encoder decoder))))
  (testing "local-timestamp-micros"
    (let [config {}
          schema (.addToSchema (LogicalTypes/localTimestampMicros) (Schema/create Schema$Type/LONG))
          decoder ((config/conversion-coder .fromLong) config schema)
          encoder ((config/conversion-coder .toLong) config schema)
          data Long/MAX_VALUE]
      (are [x f] (= (f data) x)
        (LocalDateTime/parse "+294247-01-10T04:00:54.775807") decoder
        data (comp encoder decoder)))))
