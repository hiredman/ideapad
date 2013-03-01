(ns com.thelastcitadel.page
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [hiccup.bootstrap.page :refer [include-bootstrap]])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(defn page [clojurescript javascript]
  (html
   [:html
    [:head
     [:title "ideapad"]
     (include-bootstrap)
     (include-js "/codemirror-3.1/lib/codemirror.js"
                 "/codemirror-3.1/mode/clojure/clojure.js"
                 "/codemirror-3.1/keymap/emacs.js"
                 "http://d3js.org/d3.v3.min.js"
                 "/boot.js")
     (include-css "/codemirror-3.1/lib/codemirror.css")]
    [:body
     [:div {:style "width:100%"}
      [:button {:id "save"} "Save"]
      [:span {:style "padding-left:2em;"}
       [:input {:type "text"
                :name "username"
                :style "height:1.8em;width:10em;"}]
       [:input {:type "password"
                :name "password"
                :style "height:1.8em;width:10em;"}]]]
     [:div {:id "code"
            :style "width:40%;border:1px solid black;height:100%;float:left;"}
      [:textarea {:id "editor"
                  :style "height:100%;"}
       (or clojurescript "")]
      [:textarea {:id "javascript"
                  :style "display:none"}
       (or javascript "none")]]
     [:div {:id "pad"
            :style "width:50%;height:100%;float:left;margin:3em;"}]
     [:script "boot.main();"]]]))


(defn login []
  (html
   [:html
    [:head
     [:title "ideapad"]
     (include-bootstrap)
     (include-js "/codemirror-3.1/lib/codemirror.js"
                 "/codemirror-3.1/mode/clojure/clojure.js"
                 "/codemirror-3.1/keymap/emacs.js"
                 "http://d3js.org/d3.v3.min.js"
                 "/boot.js")
     (include-css "/codemirror-3.1/lib/codemirror.css")]
    [:body
     [:form {:action "/login"
             :method "post"}
      [:input {:type "text"
               :name "username"}]
      [:input {:type "password"
               :name "password"}]
      [:input {:type "Submit"}]]]]))

