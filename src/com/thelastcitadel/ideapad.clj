(ns com.thelastcitadel.ideapad
  (:require [compojure.handler :refer [site]]
            [compojure.core :refer [defroutes ANY]]
            [ring.util.response :refer [file-response]]
            [clojure.java.io :as io]
            [ring.middleware.resource :refer [wrap-resource]]
            [clj-http.client :as http]
            [hiccup.bootstrap.middleware :refer [wrap-bootstrap-resources]]
            [cheshire.core :as json]
            [com.thelastcitadel.page :refer [page login]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [carica.core :refer [config]])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(defonce queue (LinkedBlockingQueue.))

(defonce cookies (atom nil))

(defonce storage-cookies (atom nil))

(defn deal-with-nrepl [request]
  (if (= "save" (:op (:params request)))
    (let [{:keys [body]} (http/put (config :store-url)
                                   {:body
                                    (pr-str
                                     {:clojurescript (or (:clojurescript (:params request))
                                                         "")
                                      :javascript (or (:javascript (:params request))
                                                      "")})
                                    :cookies @storage-cookies})]
      {:body (json/encode [{:id (:id (:params request))
                            :status ["done"]
                            :storage-id body}])
       :headers {"Content-Type" "application/json"}})
    (do
      (future
        (let [{:keys [body]} (http/post (config :compiler-url)
                                        {:form-params (:params request)
                                         :cookies @cookies})]
          (.put queue body)))
      {:body (or (doto (.poll queue 1 java.util.concurrent.TimeUnit/SECONDS) prn)
                 "[]")
       :headers {"Content-Type" "application/json"}})))

(def nrepl-handler (-> deal-with-nrepl
                       (friend/wrap-authorize #{::authenticated})))

(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "password")
                    :roles #{::authenticated}}})

(defroutes app
  (ANY "/" request
       {:status 200
        :body (page "" "")})
  (ANY "/login" request
       {:status 200
        :body (login)})
  (ANY "/nrepl" request nrepl-handler)
  (ANY "/pad/:id" request
       (let [{:keys [body]} (http/get (config :retrieve-url)
                                      {:query-params {:id (:id (:params request))}
                                       :cookies @storage-cookies})
             {:keys [clojurescript javascript]} (read-string body)]
         {:status 200
          :body (page clojurescript javascript)}))
  (friend/logout
   (ANY "/logout" request (ring.util.response/redirect "/"))))

(def handler (-> #'app
                 (friend/authenticate
                  {:credential-fn (partial creds/bcrypt-credential-fn users)
                   :workflows [(workflows/interactive-form)]})
                 site
                 (wrap-resource "site/")
                 wrap-bootstrap-resources))

(defn init []
  (reset! cookies
          (:cookies
           (http/post (config :compiler-login-url)
                      {:form-params (config :compiler-credentials)
                       :follow-redirects false})))
  (reset! storage-cookies
          (:cookies
           (http/post (config :storage-login-url)
                      {:form-params (config :storage-credentials)
                       :follow-redirects false})))
  (future
    (try
      (while true
        (let [{:keys [body]} (http/get (config :compiler-url) {:cookies @cookies})]
          (when (not= body "[\n\n]")
            (.put queue body)))
        (Thread/sleep 500))
      (catch Throwable t
        (prn t)))))
