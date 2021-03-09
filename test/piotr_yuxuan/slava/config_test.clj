(ns piotr-yuxuan.slava.config-test
  (:require [clojure.test :refer [deftest is are testing]]
            [byte-streams]
            [piotr-yuxuan.slava.logical-types :as logical-types]
            [piotr-yuxuan.slava.config :as config])
  (:import (org.apache.avro LogicalTypes Schema Schema$Type)
           (java.time LocalDate)))

(deftest config-keys-test
  (is (config/config-keys "schema.registry.url")))

(deftest domain-test
  (is (= :schema-registry (config/domain "auto.register.schemas")))
  (is (= :slava (config/domain :record-key-fn))))

(deftest split-domains-test
  (let [record-key-fn (constantly nil)]
    (is (= (config/split-domains {"auto.register.schemas" "http"
                                  :record-key-fn record-key-fn})
           {:schema-registry {"auto.register.schemas" "http"},
            :slava {:record-key-fn record-key-fn}}))))

;; Stateful, because Avro is stateful.
(logical-types/add-all-conversions)

(deftest conversion-coder-test
  (let [config {}
        logical-decoder-fn (config/conversion-coder .fromInt)
        logical-decoder (logical-decoder-fn config (.addToSchema (LogicalTypes/date) (Schema/create Schema$Type/INT)))]
    (testing "decoding"
      (are [x y] =
        (LocalDate/parse "1970-01-01") (logical-decoder (int 0))
        (LocalDate/parse "1973-08-30") (logical-decoder (int 1337))))
    (let [logical-encoder-fn (config/conversion-coder .toInt)
          logical-encoder (logical-encoder-fn config (.addToSchema (LogicalTypes/date) (Schema/create Schema$Type/INT)))]
      (testing "encoding"
        (are [x y] =
          0 (logical-encoder (LocalDate/parse "1970-01-01"))
          1337 (logical-encoder (LocalDate/parse "1973-08-30"))))
      (testing "round trip"
        (are [x f] (= x (f x))
          (int 0) (comp logical-encoder logical-decoder)
          (int 1337) (comp logical-encoder logical-decoder)
          (LocalDate/parse "1970-01-01") (comp logical-decoder logical-encoder)
          (LocalDate/parse "1973-08-30") (comp logical-decoder logical-encoder))))))
