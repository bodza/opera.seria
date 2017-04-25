(ns koraujkor.server
    (:require #_[clojure.core.match :refer [match]]
            #_[clojure.data.int-map :as im]
              [clojure.data.xml :as xml]
              [clojure.data.xml.event :refer [event-exit? event-node]]
              [clojure.data.xml.jvm.parse :refer [string-source]]
              [clojure.data.xml.tree :refer [seq-tree]]
              [clojure.edn :as edn]
              [clojure.java.io :as io]
              [clojure.repl :refer [source]]
              [clojure.set :as set]
              [cognitect.transit :as transit]
            #_[hiccup.core :refer [html]]
              [ring.adapter.jetty :refer [run-jetty]]
              [ring.middleware.params :refer [wrap-params]]
              [ring.middleware.resource :refer [wrap-resource]]
              [ring.middleware.content-type :refer [wrap-content-type]]
              [ring.middleware.not-modified :refer [wrap-not-modified]]
              [ring.util.request :refer [path-info]]
              [ring.util.response :refer [response not-found content-type charset]])
    (:import [java.io ByteArrayOutputStream PushbackReader]
             [clojure.data.xml.event StartElementEvent]))

(defn gc [n]
    (let [rt (Runtime/getRuntime)]
        (dotimes [_ n] (System/gc))
        (let [m (- (.totalMemory rt) (.freeMemory rt))]
            (/ m 1024.0 1024.0 #_1024.0))))

(defn lazy-seq? [edn] (instance? clojure.lang.LazySeq edn))

(defn hiccup [tag attrs contents inline]
    (let [v (transient [tag attrs])]
        (persistent! (if inline (reduce conj! v contents) (conj! v contents)))))

(defn- -long [s] (Long/parseLong s))
(defn- -double [s] (Double/parseDouble s))
(defn- -vector [s] (read-string (str \[ s \])))

(defn- as*
    ([as] (-> as (as* -long :id :ref) (as* -double :s :x :y) (as* -vector :b :i)))	; ouch!
    ([as fy & names] (reduce #(let [a (%1 %2)] (if (string? a) (assoc %1 %2 (fy a)) %1)) as names)))

(defn- letter? [^Character c] (Character/isLetter c))
(defn- digit? [^Character c] (Character/isDigit c))

(defn- cs' [edn]
    (if (string? edn)
        (->> (partition-by #(cond (letter? %) 1 (digit? %) -1 :else 0) edn)
             (mapcat #(let [c (first %)] (if (or (letter? c) (digit? c)) [(apply str %)] %))))
        [edn]))

(defn event-element
    ([inline] #(event-element %1 %2 inline))
    ([event contents inline]
        (when (instance? StartElementEvent event)
            (hiccup (:tag event) (as* (:attrs event)) (mapcat cs' contents) inline))))

(defn event-tree [events inline]
    (ffirst (seq-tree (event-element inline) event-exit? event-node events)))

(defn xml-parse [source inline & opts]
    (let [reader (when (string? source) (string-source source))]
        (event-tree (xml/event-seq (or reader source) opts) inline)))

(def ^:private nul \u0000)

(defn- pr! [^Character c]
    (when-not (nil? c) (.append *out* (if (= c nul) \space c)) nil))

(declare pr')

(defn- pr-
    ([z] (pr! z))
    ([z x] (pr- z x \space))
    ([z x z'] (pr! z) (pr x) z')
    ([z a b c] (pr- z a b c \space))
    ([z a b c z'] (let [a? (not (nil? a))] (pr! (if a? z)) (pr! a) (apply pr' (if-not a? z) b) (pr! c) z')))

(defn- pr?
    ([x] (pr? nil x))
    ([z x] (cond
        (= x \newline) (do (when *print-readably* (pr- z x)) (pr! x))
        (or (char? x) (string? x)) (pr- (if (= z nul) nil z) x (if *print-readably* \space nul))
        (vector? x)    (pr- z \[ x \])
        (map? x)       (if (or *print-readably* (seq x)) (pr- z x) z)
        (lazy-seq? x)  (if (seq x) (pr- z nil x nil) z)
        :keyword       (pr- z x))))

(defn- pr'
    ([_] nil)
    ([z x] (pr? z x) nil)
    ([z x & y] (loop [z (pr? z x) [x & y] y] (let [z (pr? z x)] (when y (recur z y))))))

(defn- pr* [& edn]
    (apply pr' nil edn))

(defn edn-print [edn pretty]
    (binding [*print-readably* (not pretty)]
        (pr* edn) (pr! \newline) (when *flush-on-newline* (flush))))

(defn edn-write [edn pretty target & opts]
    (with-open [^java.io.Writer out (apply io/writer target opts)] (binding [*out* out] (edn-print edn pretty))))

(defn edn-read [source & opts]
    (edn/read (PushbackReader. (apply io/reader source opts))))

(defn boolean? [edn] (instance? Boolean edn))

(defn- map-e? [edn] (instance? clojure.lang.IMapEntry edn))

(defn- complect [e- e s]
    (if-let [q (get s :q)]
        (if (or (keyword? e) (boolean? e)) e
            (if-let [e' (@q e)] e'
                (let [e' (if (coll? e) e (if (= e- e) e- e))] (swap! q conj e') e')))
        (if (= e- e) e- e)))

(defn walk
    ([i o e] (walk i o e nil))
    ([i o e s] (let [e' (cond (or (char? e) (string? e) (keyword? e) (number? e) (boolean? e)) e
                                  (map-e? e) (let [[k v] e] (clojure.lang.MapEntry. (i 0 k) (i 1 v)))
                              (or (map? e) (vector? e)) (into (empty e) (map-indexed i e)))]
                    (assert (not (nil? e')) (str "unexpected " (type e)))
                    (complect e (o e' s) s))))

(defn- step'
    ([e s] e)
    ([e s n] (if-let [p (get s :p)] (assoc s :p (conj p n)) s)))

(defn walk- ([f e] (walk- f e nil)) ([f e s] (walk #(walk- f %2 (f %2 s %1)) f e s)))
(defn -walk ([f e] (-walk f e nil)) ([f e s] (walk #(-walk f %2 (f %2 s %1)) step' (f e s) s)))

(defn verso [f m]
    (persistent! (reduce (fn [m' [k v]] (if-let [k' (f k)] (assoc! m' v k') m')) (transient (empty m)) m)))
(defn verso* [g m]
    (persistent! (reduce (fn [m' [k v]] (assoc! m' v (conj (get m' v #{}) k))) (transient (empty m)) (g m))))

(defn- chord [c]
    (case c \space :'s \newline :'n (keyword (let [x (int c)] (format (if (< x 0x100) "'%02x" "'%04x") x)))))

(defn rhz-make [edn]
    (let [m (atom {}) q (atom #{}) i (atom 0) r (keyword (str \. (swap! i inc)))
          step (fn ([e s] (if-let [p (get s :p)] (if-let [e' (@m e)] e'
                            (let [[e' e-] (cond
                                    (char? e)    [(chord e) nil]
                                    (string? e)  (let [k (keyword e)] [k (name k)])
                                    (keyword? e) [(keyword (str \. (name e))) nil]
                                    (vector? e)  [(keyword (str \. (swap! i inc))) (get @q e e)])]
                                (assert (not (nil? e')) (str "unexpected " (type e)))
                                (when e- (swap! m assoc e- e'))
                                e'))
                            (if (keyword? e) (keyword (str \. (name e))) e)))
                   ([e s n] (step' e (if (map? e) (dissoc s :p) s) n)))]
        (let [w (walk- step edn {:q q :p []})]
            {:pages (as-> (verso #(when (vector? %) %) @m) v (assoc v r (v w)) (dissoc v w))
             :words (verso #(when (string? %) {}) @m)})))

(defonce -monok (delay (rhz-make (xml-parse (io/reader (io/resource "koraujkor/data/monok.xml")) true))))

(defn- span-x [pages words]
    (verso* #(for [[k v] % w v :when (words w)] [k w]) pages))
(defn- page-x [pages words]
    (verso* #(for [[k [t]] % :when (= t :.p) w (tree-seq pages pages k) :when (words w)] [k w]) pages))

(defonce monok (delay (let [pages (edn-read "monok/pages.edn") words (edn-read "monok/words.edn")]
    {:pages pages :words words :index (page-x pages words)})))

(defn rhz-print [m]
    (assert (map? m) (str "unexpected " (type m)))
    (pr! \{) (pr! \newline)
    (doseq [[k v] (sort m)] (pr k) (pr! \space) (pr v) (pr! \newline))
    (pr! \}) (pr! \newline) (when *flush-on-newline* (flush)))

(defn rhz-write [edn target & opts]
    (with-open [^java.io.Writer out (apply io/writer target opts)] (binding [*out* out] (rhz-print edn))))

(defn- query [request]
    (let [epoch (System/nanoTime)
          p* (:pages @monok)
          q* (map keyword (re-seq #"(?U)\p{Alnum}+" (get-in request [:params "q"] "")))
          r* (map (:index @monok) q*)
          s* (if (seq r*) (apply set/intersection r*) r*)
          t* (frequencies (map #(get-in p* [(get-in p* [% 3]) 1 :.t]) s*))
          elapsed (format "%.1f ms" (/ (double (- (System/nanoTime) epoch)) 1e6))]
        {:q* q* :r* r* :t* t* :elapsed elapsed}))

(defn- transit [request]
    (let [baos (ByteArrayOutputStream. 4096) w (transit/writer baos :json) _ (transit/write w (query request))] (.toString baos "utf-8")))

(defn- servlet' [request]
    (if (= (path-info request) "/transit")
        (-> (response (transit request)) (content-type "application/transit+json") (charset "utf-8"))
        (-> (not-found "404 Not Found")  (content-type "text/plain")               (charset "utf-8"))))
(def servlet
    (-> servlet' (wrap-params) (wrap-resource "public") (wrap-content-type) (wrap-not-modified)))

(defonce server (run-jetty #'servlet {:port 9802 :join? false}))
