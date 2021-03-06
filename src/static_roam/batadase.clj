(ns static-roam.batadase
  (:require [static-roam.utils :as utils]
            [static-roam.config :as config]
            [org.parkerici.multitool.core :as u]
            [clojure.set :as set]
            [clojure.string :as str]
            [taoensso.truss :as truss :refer (have have! have?)]
            ))

;;; Database accessors. The name is a PROTEST against the feature of Clojure I hate most, rigid limits on namespace reference

(defn block? [x]
  (and (map? x)
       (string? (:id x))))

;;; included? means reachable from an entry point
;;; displayed? means actually generated. Usually these are the same, except when the :unexcluded? config is set, meaning we want to see everything, included or not.

(defn included?
  ([block]
   (assert (block? block))
   (:include? block))
  ([block-map block-id]
   (included? (get block-map block-id))))

(defn displayed?
  ([block]
   (assert (block? block))
   (if (config/config :unexclude?)
     true
     (:include? block)))
  ([block-map block-id]
   (displayed? (get block-map block-id))))

(defn- get-linked-references
  [block-id block-map]
  (filter #(get-in block-map [% :id])      ;trying to filter out bogus entries, not working
          (get-in block-map [block-id :linked-by])))

(defn get-displayed-linked-references
  [block-id block-map]
  (filter (partial displayed? block-map)
          (get-linked-references block-id block-map)))

;;; Some new accessors

(def descendents
  (u/transitive-closure :dchildren))

(defn block-parent
  [block-map block]
  (and (:parent block)
       (get block-map (:parent block))))

(defn block-children
  [block-map block]
  (map block-map (:children block)))

(defn block-descendents
  [block]
  ((u/transitive-closure :dchildren) block))

(defn block-contains?
  [b1 b2]
  (contains? (set (map :id (descendents b1))) (:id b2)))

(defn forward-page-refs
  "Forward page refs. Returns set of ids"
  [page]
  (apply clojure.set/union
         (map :refs (filter displayed? (block-descendents page)))))

(defn block-page
  [block-map block]
  (if-let [parent (block-parent block-map block)]
    (block-page block-map parent)
    block))

(defn backward-page-refs
  [bm page]
  (map :content
       (filter displayed?
               (map (comp (partial block-page bm) bm)
                    (:linked-by page)))))

(defn page-refs
  [bm page]
  (set/union (forward-page-refs page)
             (backward-page-refs bm page)))

(defn- pages
  [block-map]
  (filter :page? (vals block-map)))

(defn included-pages
  [block-map]
  (filter included? (pages block-map)))

(defn displayed-pages
  [block-map]
  (filter displayed? (pages block-map)))

(defn displayed-blocks
  [block-map]
  (filter displayed? (vals block-map)))



(defn displayed-regular-pages
  [block-map]
  (remove :special? (displayed-pages block-map)))

(defn tagged?
  [block-map block tag]
  (or (contains? (:refs block) tag)
      ;; This implements the somewhat weird convention that tags are done in contained elts, eg
      ;; - Some private stuff
      ;;   - #Private
      ;; partly for historical reasons and partly so pages can be tagged
      (some #(contains? (:refs %) tag)
            (block-children block-map block))))

(defn tagged-or-contained?
  [block-map block tag]
  (and block
       (or (tagged? block-map block tag)
           (tagged-or-contained? block-map (block-parent block-map block) tag))))

(defn entry-point?
  "Determines whether or not a given page is tagged with #EntryPoint in its first child block"
  [block-map block]
  (some #(tagged? block-map block %)
        (config/config :entry-tags)))

(def fixed-entry-points #{"SR Metadata"})

(defn entry-points
  [block-map]
  (filter (partial entry-point? block-map) (pages block-map)))

(def daily-notes-regex #"(?:January|February|March|April|May|June|July|August|September|October|November|December) \d+.., \d+")

(defn daily-notes?
  [block-map block]
  (let [page (block-page block-map block)
        title (or (:title page) (:content page))]
    (when title (re-matches daily-notes-regex title))))

(defn exit-point?
  [block-map block]
  (or (some #(tagged-or-contained? block-map block %)
            (config/config :exit-tags))
      (and (not (config/config :daily-notes?))
           (daily-notes? block-map block))))

;;; Temp
(def min* (partial u/min-by identity))
(def max* (partial u/max-by identity))

;;; Prob needs to deal with missing data
;;; Also, to be proper, :create-time should be used as well
;;; I suppose the median time might be more informative – or an Ed Tufte minigraph
(defn date-range [page]
  (let [blocks (block-descendents page)
        visible-blocks (if (config/config :unexclude?)
                          blocks
                          (filter :include? blocks))
        visible-dates (filter identity (map :edit-time visible-blocks))]
    [(min* visible-dates) (max* visible-dates)]))

(defn stats [bm]
  {:blocks {:total (count bm)
            :published (count (filter :include? (vals bm)))}
   :pages {:total (count (pages bm))
           :published (count (filter :include? (pages bm)))}})


;;; These could be part of the map but it's easier this way

(u/defn-memoized edit-time
  [page]
  (second (date-range page)))

(u/defn-memoized size
  [page]
  (reduce +
          (count (:content page ""))
          (map size
               (filter displayed? (:dchildren page)))))

(defn page-empty?
  [page]
  (and (not (:special? page))
       (< (- (size page)
             (count (:id page)))
          10)))

(defn expand-to [block-map block minsize]
  (cond (>= (size block) minsize)
        block
        (nil? (:parent block))
        block
        :else
        (expand-to block-map (get block-map (:parent block)) minsize)))

(defn leaf?
  [block]
  (empty? (:chidlren block)))

(defn all-refs [block]
  (set/union
   (set (:children block))
   (set (:refs block))
   (set (:linked-by block))
   (set (and (:parent block) (list (:parent block))))))
