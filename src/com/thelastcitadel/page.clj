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
     (include-js "/codemirror/lib/codemirror.js"
                 "/codemirror/addon/edit/closebrackets.js"
                 "/codemirror/mode/clojure/clojure.js"
                 "/codemirror/keymap/emacs.js"
                 "/codemirror/addon/edit/matchbrackets.js"
                 "http://d3js.org/d3.v3.min.js"
                 "//ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"
                 "//ajax.googleapis.com/ajax/libs/angularjs/1.0.4/angular.min.js"
                 "//ajax.googleapis.com/ajax/libs/jqueryui/1.10.1/jquery-ui.min.js"
                 "/boot.js")
     (include-css "/codemirror/lib/codemirror.css")
     [:style "
.CodeMirror {
  border: 1px solid #eee;
  height: auto;
}
.CodeMirror-scroll {
  overflow-y: hidden;
  overflow-x: auto;
}

.text-success {
color:#468847;
}
 .text-error {
color:#b94a48;
}
"]]
    [:body
     [:div {:style "width:100%"}
      [:button {:id "save"} "Save"]
      [:span {:style "padding-left:2em;padding-right:2em;"}
       [:input {:type "text"
                :name "username"
                :style "height:1.8em;width:10em;"
                :id "username"}]
       [:input {:type "password"
                :name "password"
                :style "height:1.8em;width:10em;"
                :id "password"
                :onChange "boot.try_login()"}]]
      [:span {:id "loggedin"
              :class "text-error"}
       "not logged in"]]
     [:div {:id "code"
            :style "width:40%;border:1px solid black;height:90%;float:left;"}
      [:textarea {:id "editor"
                  :style "height:90%;"
                  :autocomplete "off"}
       (or clojurescript "")]
      [:textarea {:id "javascript"
                  :style "display:none"
                  :autocomplete "off"}
       (or javascript "")]]
     [:div {:id "pad"
            :style "width:50%;height:90%;float:left;margin-left:3em;"}]
     (include-js "http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_SVG"
                 "http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML")
     [:script "boot.main();"]]]))


(defn login []
  (html
   [:html
    [:head
     [:title "ideapad"]
     (include-bootstrap)
     (include-js "/codemirror/lib/codemirror.js"
                 "/codemirror/mode/clojure/clojure.js"
                 "/codemirror/keymap/emacs.js"
                 "http://d3js.org/d3.v3.min.js"
                 "/boot.js")
     (include-css "/codemirror/lib/codemirror.css")]
    [:body
     [:form {:action "/login"
             :method "post"}
      [:input {:type "text"
               :name "username"}]
      [:input {:type "password"
               :name "password"}]
      [:input {:type "Submit"}]]]]))

