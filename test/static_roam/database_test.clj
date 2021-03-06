(ns static-roam.database-test
  (:require [static-roam.database :refer :all]
            [static-roam.parser :as parser]
            [clojure.test :refer :all]))

(deftest content-refs-test
  ;; Note: Roam will sometimes include .s in hashtags, but it seems inconsistent
  (is (= #{"wizards" "light"}
         ;; This is trick for referring to non-public fn, good to know...
         (#'block-refs {:parsed (parser/parse-to-ast "Do not meddle in the affairs of #wizards, because they become soggy and hard to #light.")}  ))))
