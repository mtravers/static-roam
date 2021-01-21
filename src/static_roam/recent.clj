(ns static-roam.recent
  (:require [static-roam.utils :as utils]
            [static-roam.database :as database]
            [static-roam.templating :as templating]
            [clojure.pprint :as pprint]))

;;; TODO filter out empty blocks
;;; TODO link path isnt right, formatting
;;; TODO if there are outline-nested blocks, they will repeat, which looks stupid

(defn recents
  [block-map]
  "Groups recently changed blocks by page, returns rev chron seq of seqs"
  (->> (take 100 (reverse (sort-by :edit-time (filter :included (vals block-map)))))
       (map #(assoc % :page (templating/find-page (:id %) block-map)))
       (group-by :page)
       vals
       (sort-by (fn [blocks] (reduce max 0 (map :edit-time blocks))))
       reverse))

(defn render-time
  [time]
  (str (java.util.Date. time)))         ;crude for now

(defn recent-page-content
  [block-map]
  `[:div
    ~@(for [group (recents block-map)
            :let [page (:page (first group))
                  edit-time (reduce max 0 (map :edit-time group))]]
        `[:div
          "From " ~(templating/page-link page)
          " "
          [:span ~(render-time edit-time)]
          ~@(for [block (take 3 group)] ;limit to 3 chunks
              [:div (templating/children-of-block-template (:id block) block-map)])])])


