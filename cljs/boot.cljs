(ns boot
  (:require [drawbridge.client :as repl]
            [goog.events :as events]
            [goog.net.XhrIo]
            [goog.Uri.QueryData]))

(def pad)

(defn ^:export load-js [url]
  (.appendChild
   (aget (.getElementsByTagName js/document "head") 0)
   (doto (.createElement js/document "script")
     (.setAttribute "type" "text/javascript")
     (.setAttribute "src" url))))

(def codemirror (atom nil))

(defn setup-code []
  (reset! codemirror
          (.fromTextArea
           js/CodeMirror
           (.getElementById js/document "editor")
           (js-obj "lineNumbers" true
                   "matchBrackets" true
                   "extraKeys"
                   (js-obj "Shift-Enter"
                           (fn [cm]
                             (let [r (repl/nrepl-send "/nrepl" {:op "compile-cljs"
                                                                :code
                                                                (str "(do " (.getValue cm) " )")})]
                               (add-watch r :close
                                          (fn [k this _ v]
                                            (when (= "done"
                                                     (aget (get (last v) "status") 0))
                                              (repl/nrepl-close this)
                                              (remove-watch this k))))
                               (add-watch r :done
                                          (fn [k this _ v]
                                            (when (= "done" (aget (get (last v) "status") 0))
                                              (try
                                                (set! (.-value (.getElementById js/document "javascript"))
                                                      (get (first v) "javascript"))
                                                ((js/eval (get (first v) "javascript")))
                                                (catch js/Object e
                                                  (set! (.-innerHTML pad) (str e))))))))))))))

(defn ^:export save [& _]
  (let [r (repl/nrepl-send "/nrepl" {:op "save"
                                     :clojurescript (.getValue @codemirror)
                                     :javascript (.-value (.getElementById js/document "javascript"))})]
    (add-watch r :close
               (fn [k this _ v]
                 (when (= "done"
                          (aget (get (last v) "status") 0))
                   (repl/nrepl-close this)
                   (remove-watch this k))))
    (add-watch r :done
               (fn [k this _ v]
                 (when (= "done" (aget (get (last v) "status") 0))
                   (set! (.-location js/window) (str "/pad/" (get (first v) "storage-id"))))))))

(defn ^:export try-login []
  (let [username (.getElementById js/document "username")
        password (.getElementById js/document "password")]
    (let [request (goog.net.XhrIo.)]
      (events/listen request "complete"
                     (fn [e]
                       (when (not= "false" (.getResponseHeader request "X-In-Like-Flynn"))
                         (doto (.getElementById js/document "loggedin")
                           (-> .-innerHTML (set! "logged in"))
                           (.setAttribute "class" "text-success")))
                       (.dispose request)))
      (.send request "/login" "POST"
             (.toString
              (doto (goog.Uri.QueryData.)
                (.add "username" (.-value username))
                (.add "password" (.-value password))))))))

(defn ^:export main []
  (set! pad (.getElementById js/document "pad"))
  (setup-code)
  (let [save-button (.getElementById js/document "save")]
    (set! (.-onclick save-button) save))
  (when-let [f (js/eval (.-value (.getElementById js/document "javascript")))]
    (f))
  (try-login))

(def colors
  ["steelblue" "green" "darkorange" "PaleVioletRed" "OliveDrab" "LightSteelBlue" "Indigo"
   "Fuchsia"])

(defn ^:export color-pool []
  (let [colors (atom (set colors))]
    (fn [item fun]
      (let [c (rand-nth @colors)]
        (swap! colors disj c)
        (fun c item)
        item))))

(defn agg-fun [data agg fun]
  (apply agg (for [[_ vs] data
                   v (map-indexed fun vs)]
               v)))

(defn average-dispersal [data fun]
  (let [x (for [[_ vs] data
                [a b] (partition 2 (map-indexed fun vs))]
            (- a b))]
    (/ x (count x))))

(defn frame [g x y w max-y]
  (doto g
    (-> (.append "svg:line")
        (.attr "x1", (x 0))
        (.attr "y1", (* -1 (y 0)))
        (.attr "x2", (x w))
        (.attr "y2", (* -1 (y 0)))
        (.style "stroke" "black"))
    (-> (.append "svg:line")
        (.attr "x1", (x 0))
        (.attr "y1", (* -1 (y 0)))
        (.attr "x2", (x 0))
        (.attr "y2", (* -1 (y max-y)))
        (.style "stroke" "black"))
    (-> (.selectAll ".xLabel")
        (.data (.ticks x 5))
        (.enter)
        (.append "svg:text")
        (.attr "class" "xLabel")
        (.text js/String)
        (.attr "x" (fn [d] (x d)))
        (.attr "y" 0)
        (.attr "text-anchor" "middle"))))

(defn ^:export line-chart [data {:keys [w h margin]
                                 x-fun :x
                                 y-fun :y}]
  (let [max-x (agg-fun data max x-fun)
        max-y (agg-fun data max y-fun)
        x (-> js/d3
              (.-scale)
              (.linear)
              (.domain (array 0 max-x))
              (.range (array (+ 0 margin)
                             (- w margin))))
        y (-> js/d3
              (.-scale)
              (.linear)
              (.domain (array 0 max-y))
              (.range (array (+ 0 margin)
                             (- h margin))))
        vis (-> js/d3
                (.select "#pad")
                (.append "svg:svg")
                (.attr "width" w)
                (.attr "height" h))
        g (-> vis
              (.append "svg:g")
              (.attr "transform" (str "translate(0,"h")")))
        line (-> js/d3
                 (.-svg)
                 (.line)
                 (.x (fn [d i] (x (x-fun i d))))
                 (.y (fn [d i] (* -1 (y (y-fun i d))))))
        line-color (color-pool)
        color-labels (atom {})]
    (frame g x y w max-y)
    (doseq [[label values] data
            :let [values (apply array values)]]
      (-> g
          (.append "svg:path")
          (.attr "d" (line values))
          (.style "fill" "none")
          (.style "stroke-width" 2)
          (line-color (fn [c line]
                        (swap! color-labels assoc label c)
                        (.style line "stroke" c)))))))

;; (line-chart {"foo" [1 3 6 10 20 21 21 21 50]}
;;             {:w 1024
;;              :h 800
;;              :margin 20
;;              :x (fn [i d] i)
;;              :y (fn [i d] d)})

(defn svg* [thing]
  (cond
   (string? thing) thing
   (seq? thing) (apply str (map svg* thing))
   :else (let [[tag atrs? & content] thing
               [attrs content] (if (map? atrs?)
                                 [atrs? content]
                                 [atrs? (cons atrs? content)])]
           (str "<" (name tag) " "
                (apply str
                       (interpose \space
                                  (for [[n v] attrs]
                                    (str (name n) "=" (pr-str v)))))
                ">"
                (apply str (map svg* content))
                "</" (name tag) ">"))))

(defn svg [thing]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
       (svg* thing)))

(defn linear-scale [& {[domain-start domain-end] :domain
                       [range-start range-end] :range}]
  (let [scale-factor (/ (- range-end range-start)
                        (- domain-end domain-start))]
    (fn [x]
      (+ (* scale-factor (- x domain-start)) range-start))))
