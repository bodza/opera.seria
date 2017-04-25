(ns koraujkor.client
    (:require [reagent.core :as reagent]
              [cognitect.transit :as transit])
    (:import [goog.net XhrIo]))

(enable-console-print!)

(def -model
{
    :credits {
        :enabled false
    }
})

(defn- chart [style model]
    (reagent/create-class {:reagent-render #(do [:div {:style style}])
                           :component-did-mount #(.highcharts (js/$ (reagent/dom-node %)) (clj->js (merge -model model)))}))

(defn- chart' [style model']
    (reagent/create-class {:reagent-render #(do [:div {:style style}])
                           :component-did-mount #(.highcharts (js/$ (reagent/dom-node %)) (clj->js (merge -model (model'))))
                           :component-did-update #(.highcharts (js/$ (reagent/dom-node %)) (clj->js (assoc-in (merge -model (model'))
                                                                                                        [:plotOptions :series :animation] false)))}))

(.setOptions js/Highcharts (clj->js
{
    :lang {
        :months      ["január" "február" "március" "április" "május" "június" "július" "augusztus" "szeptember" "október" "november" "december"]
        :shortMonths ["jan"    "feb"     "már"     "ápr"     "máj"   "jún"    "júl"    "aug"       "szept"      "okt"     "nov"      "dec"     ]
    }
}))

(defn- color [n]
    (nth (.. js/Highcharts getOptions -colors) n))
(defn- rgba [rgb a]
    (.. js/Highcharts (Color (clj->js rgb)) (setOpacity a) (get "rgba")))
(defn- brighten [rgb x]
    (.. js/Highcharts (Color (clj->js rgb)) (brighten x) (get "rgb")))
(defn- brighten' [c x]
    (.. js/Highcharts (Color (clj->js c)) (brighten x) get))

(def gradient2
    (into [] (map #(do {:radialGradient {:cx 0.5 :cy 0.3 :r 0.7} :stops [[0, %] [1, (brighten % -0.3)]]}) ; darken
        (.. js/Highcharts getOptions -colors))))

(def gradient3
    (into [] (map #(do {:radialGradient {:cx 0.4 :cy 0.3 :r 0.5} :stops [[0, %] [1, (brighten % -0.2)]]}) ; darken
        (.. js/Highcharts getOptions -colors))))

(defn- date [y m]
    (.UTC js/Date y m))

(defonce data (reagent/atom {}))

(defn- query [q]
    (let [r (transit/reader :json) read #(transit/read r %)]
        (XhrIo.send (str "/transit?q=" q) #(reset! data (read (.getResponseText (.-target %)))))))

(defn- input []
    (let [orna "css/codlat370/init1.jpeg" bs 131 ps 39046 ws 11500373
          q (reagent/atom "") edit #(reset! q (-> % .-target .-value)) stop #(reset! q "") save #(query @q)]
        (fn [] [:div.rare0_head1 {:style {:width "948px" :text-align "center"}} [:div.leaflet0
            [:img {:src orna :style {:position "absolute" :top "8px" :left "8px" :height "36px"}}]
            [:input {:type "text" :value @q :placeholder (str "keresés " bs " szakirodalmi forrás " ps " oldalán " ws " szó teljes szövegében")
                     :on-blur save :on-change edit :on-key-down #(case (.-which %) (13 32) (save) 27 (stop) nil)
                             :style {:font-size "18px" :height "26px" :width "740px" :margin "8px 8px 7px" :vertical-align "top"}}]
            [:img {:src orna :style {:position "absolute" :top "8px" :right "8px" :height "36px"}}]]])))

(defn- q' [m] (apply str (interpose " + " (map name (:q* m)))))

(defn- chart- [] (let [_ @data]
    [chart' {:min-width "300px" :max-width "900px" :margin "0 auto 200px"}
(fn [] (let [data' (vec (sort-by second (comp - compare) (:t* @data)))
             data- (into data' (repeat (- 30 (count data')) ["" nil]))
             data* (mapv (fn [q r] [(name q) (count r)]) (:q* @data) (:r* @data))] {
    :chart {
        :type "bar"
        :backgroundColor "rgba(0,0,0,0)"
        :height (max 300 (* (count data-) 30))	; ouch!
    }
    :title {
        :text (let [q (q' @data)] (when (seq q)
            (let [bs (count data') ps (reduce + (map second data')) ms (:elapsed @data)]
                (str q " → " bs " műben " ps " oldalon fordul elő (" ms ")"))))
    }
    :legend {
        :enabled false
    }
    :xAxis {
        :type "category"
    }
    :yAxis {
        :visible false
    }
    :tooltip {
        :headerFormat ""
        :pointFormat "{point.name}: <b>{point.y}</b> találat"
    }
    :plotOptions {
        :bar {
            :dataLabels {
                :enabled true
            }
        }
    }
    :colors gradient2
    :series [{
        :data data-
    }, {
        :type "pie"
        :data data*
        :center [400, (if (seq data') 480 240)]
        :size (if (seq data') 240 360)
        :innerSize (if (seq data') 60 90)
        :shadow true
        :showInLegend false
        :tooltip {
            :headerFormat ""
            :pointFormat "<b>{point.y:.0f}</b> {point.name}: <b>{point.percentage:.2f}</b>%"
        }
        :allowPointSelect true
        :visible (< 1 (count data*))
    }]
}))]))

(defn koraujkor []
    (let [u- "http://koraujkor.ek.szte.hu" t- "Kora újkori olvasmánytörténeti források"]
        [:div.page
            [:div.poster0 (for [i [0 1 3 4 6 7 9]] (let [id (str "aves" i)] [:div {:id id :key id}]))]
            [:table.droplet0 {:style {:width "auto" :margin "0 auto"}}
                [:tr [:td.headlet0 [:div.rare0_head0
                    (with-meta [input] {:component-did-mount #(.focus (reagent/dom-node %))})	; woah!
                    [:a.rare0_head2 {:href u- :style {:left "52%"}} t-]]]]
                [:tr [:td [chart-]]]]]))

(reagent/render-component [koraujkor] (. js/document (getElementById "koraujkor")))
