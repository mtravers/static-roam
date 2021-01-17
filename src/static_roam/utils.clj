(ns static-roam.utils
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.data.json :as json])
  (:import (java.util.zip ZipFile)))


(defn latest-export
  []
  (->> "~/Downloads"
       fs/expand-home
       fs/list-dir
       (filter #(s/includes? (str %) "Roam-Export" ))
       (sort-by fs/mod-time)
       last
       str))

(defn unzip-roam-json
  "Takes the path to a zipfile `source` and unzips it to `target-dir`, returning the path of the target file"
  [source target-dir]
  (str target-dir (with-open [zip (ZipFile. (fs/file source))]
                    (let [entries (enumeration-seq (.entries zip))
                          target-file #(fs/file target-dir (str %))
                          database-file-name (.getName (first entries))]
                      (doseq [entry entries :when (not (.isDirectory ^java.util.zip.ZipEntry entry))
                              :let [f (target-file entry)]]
                        (fs/mkdirs (fs/parent f))
                        (io/copy (.getInputStream zip entry) f))
                      database-file-name))))

(defn read-roam-json-from-zip
  [path-to-zip]
  (let [json-path (unzip-roam-json
                   path-to-zip
                   (->> path-to-zip
                        (#(s/split % #"/"))
                        drop-last
                        (s/join "/") (#(str % "/"))))
        roam-json (json/read-str (slurp json-path) :key-fn keyword)]
    roam-json))

(defn remove-n-surrounding-delimiters
  "Removes n surrounding characters from both the beginning and end of a string"
  [n string]
  (subs string n (- (count string) n)))

(defn remove-double-delimiters
  "Removes 2 surrounding characters from both the beginning and end of a string"
  [string]
  (remove-n-surrounding-delimiters 2 string))

(defn remove-triple-delimiters
  "Removes 3 surrounding characters from both the beginning and end of a string"
  [string]
  (remove-n-surrounding-delimiters 3 string))

(defn strip-chars
  "Removes every character of a given set from a string"
  [chars collection]
  (reduce str (remove #((set chars) %) collection)))

(defn remove-leading-char
  [string]
  (subs string 1))

(defn remove-double-colon
  [string]
  (apply str (drop-last 2 string)))

(defn page-title->html-file-title
  "Formats a Roam page title as a name for its corresponding HTML page (including '.html' extension)"
  ([string]
   {:pre [(string? string)]}
   (->> string
        (s/lower-case)
        (strip-chars #{\( \) \[ \] \? \! \. \@ \# \$ \% \^ \& \* \+ \= \; \: \" \' \/ \\ \, \< \> \~ \` \{ \}})
        (#(s/replace % #"\s" "-"))
        (#(str "/" % ".html"))))
  ([string case-sensitive?]
   {:pre [(string? string)]}
   (->> string
        (#(if case-sensitive?
            %
            (s/lower-case %)))
        (strip-chars #{\( \) \[ \] \? \! \. \@ \# \$ \% \^ \& \* \+ \= \; \: \" \' \/ \\ \, \< \> \~ \` \{ \}})
        (#(s/replace % #"\s" "-"))
        (#(str "./" % ".html")))))

(defn html-file-title
  [page-title]
  (subs (page-title->html-file-title page-title :case-sensitive) 1))

(defn page-link-from-title
  "Given a page and a directory for the page to go in, create Hiccup that contains the link to the HTML of that page"
  ([dir block-content]
   [:a {:href (str dir (page-title->html-file-title block-content :case-sensitive))} block-content])
  ([block-content]
   [:a {:href (page-title->html-file-title block-content :case-sensitive)} block-content])
  ([dir block-content link-class]
   [:a {:class link-class
        :href (str dir (subs (page-title->html-file-title block-content :case-sensitive) 1))}
    block-content]))

(defn find-content-entities-in-string
  [string]
  (when (not (nil? string))
    (re-seq #"\[\[.*?\]\]|\(\(.*?\)\)" string)))

(defn find-hashtags-in-string
  [string]
  (when (not (nil? string))
    (re-seq #"\#..*?(?=\s|$)" string)))

(defn find-metadata-in-string
  [string]
  (when (not (nil? string))
    (re-seq #"^.+?::" string)))

(defn remove-heading-parens
  [strings]
  (map
   #(if (= "(((" (subs % 0 3))
      (subs % 1)
      %)
   strings))
