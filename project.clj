(defproject com.thelastcitadel/ideapad "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.1.3"]
                 [ring/ring-core "1.1.8"]
                 [compojure "1.1.5"]
                 [drawbridge-cljs "0.0.1"]
                 [clj-http "0.5.5"]
                 [cheshire "5.0.2"]
                 [hiccup-bootstrap "0.1.1"]
                 [hiccup "1.0.2"]
                 [com.cemerick/friend "0.1.3"]
                 [sonian/carica "1.0.2"]]
  :plugins [[lein-ring "0.8.3"]
            [lein-cljsbuild "0.3.0"]]
  :ring {:handler com.thelastcitadel.ideapad/handler
         :init com.thelastcitadel.ideapad/init}
  :cljsbuild {:builds [{:source-paths ["cljs"]
                        :compiler {:output-to "resources/site/boot.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]})
