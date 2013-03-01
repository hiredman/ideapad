(ns boot
  (:require [drawbridge.client :as repl]))

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

(defn ^:export main []
  (set! pad (.getElementById js/document "pad"))
  (setup-code)
  (let [save-button (.getElementById js/document "save")]
    (set! (.-onclick save-button) save))
  (when-let [f (js/eval (.-value (.getElementById js/document "javascript")))]
    (f)))






