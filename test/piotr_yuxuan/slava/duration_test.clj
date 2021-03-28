(ns piotr-yuxuan.slava.duration-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.slava.duration :as duration]))

(deftest duration-test
  (testing "duration"
    (is (= (map byte (duration/to-byte-array {:months 1, :days 256, :milliseconds 65536}))
           (->> [1 0 0 0 ; months, little-endian
                 0 1 0 0 ; days, little-endian
                 0 0 1 0 ; milliseconds, little-endian
                 ])))
    (is (= {:months 1, :days 256, :milliseconds 65536}
           (->> [1 0 0 0 ; months, little-endian
                 0 1 0 0 ; days, little-endian
                 0 0 1 0 ; milliseconds, little-endian
                 ]
                (map byte)
                byte-array
                duration/from-byte-array)))))
