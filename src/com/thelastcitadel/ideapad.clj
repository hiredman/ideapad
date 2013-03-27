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
            [carica.core :refer [config]]
            [clojure.pprint :as p])
  (:import (java.util.concurrent LinkedBlockingQueue
                                 ConcurrentHashMap)
           (java.util UUID)))

(defonce queues (agent {}))

(set-error-handler! queues (fn [_ e]
                             (.printStackTrace e)))

(add-watch queues :print (fn [k r ov nv]
                           (when-not (= ov nv)
                             (p/pprint nv))))

(defn deal-with-nrepl [request]
  (if (= "save" (:op (:params request)))
    (let [{:keys [cookies]} (http/post (config :storage-login-url)
                                       {:form-params (config :storage-credentials)
                                        :follow-redirects false})
          {:keys [body]} (http/put (config :store-url)
                                   {:body
                                    (pr-str
                                     {:clojurescript (or (:clojurescript (:params request))
                                                         "")
                                      :javascript (or (:javascript (:params request))
                                                      "")})
                                    :cookies cookies})]
      {:body (json/encode [{:id (:id (:params request))
                            :status ["done"]
                            :storage-id body}])
       :headers {"Content-Type" "application/json"}})
    (let [queue-id (or (:queue-id (:session request))
                       (str (UUID/randomUUID)))
          {:keys [received]} (get @queues (:queue-id (:session request)))]
      (send-off queues (fn f [queues]
                         (if (contains? queues queue-id)
                           (let [{{:keys [cookies received]} queue-id} queues
                                 {:keys [body]} (http/post (config :compiler-url)
                                                           {:form-params (:params request)
                                                            :cookies cookies})]
                             (.put received body)
                             queues)
                           (let [{:keys [cookies]} (http/post (config :compiler-login-url)
                                                              {:form-params (config :compiler-credentials)
                                                               :follow-redirects false})]
                             (prn "send compile request")
                             (send-off *agent* f)
                             (update-in queues [queue-id] merge
                                        {:cookies cookies
                                         :received (LinkedBlockingQueue.)})))))
      {:body (doto (or (when received
                         (.poll received 1 java.util.concurrent.TimeUnit/SECONDS))
                       "[]")
               prn)
       :headers {"Content-Type" "application/json"}
       :session (assoc (:session request)
                  :queue-id queue-id)})))

(def nrepl-handler (-> deal-with-nrepl
                       (friend/wrap-authorize #{::authenticated})))

(def users-service-url (atom nil))

(defn users-configure []
  (let [{:keys [cookies]} (http/post (str config :user-config-url "login")
                                     {:form-params (config :user-credentials)
                                      :follow-redirects false})
        {:keys [trace-redirects]
         {:strs [location]} :headers} (http/post (config :user-config-url)
                                                 {:cookies cookies
                                                  :form-params {:jdbc-url (config :user-db-url)
                                                                :table (config :user-db-table)}})]
    (reset! users-service-url (str (config :user-config-url) location))))

(defn users [username]
  (when-not @users-service-url
    (users-configure))
  (let [{:keys [cookies]} (http/post (str config :user-config-url "login")
                                     {:form-params (config :user-credentials)
                                      :follow-redirects false})
        {:keys [body]} (http/get (str @users-service-url "/" username)
                                 {:cookies cookies})]
    (read-string body)))

(defn get-pad [request]
  (let [{:keys [cookies]} (http/post (config :storage-login-url)
                                     {:form-params (config :storage-credentials)
                                      :follow-redirects false})
        {:keys [body]} (http/get (config :retrieve-url)
                                 {:query-params {:id (:id (:params request))}
                                  :cookies cookies})
        {:keys [clojurescript javascript]} (read-string body)]
    {:status 200
     :body (page clojurescript javascript)
     :headers {"content-type" "text/html"}}))

(defroutes app
  (ANY "/" request
       {:status 200
        :body (page "" "")
        :headers {"content-type" "text/html"}})
  (ANY "/login" request
       {:status 200
        :headers {"content-type" "text/html"}
        :body (login)})
  (ANY "/nrepl" request nrepl-handler)
  (ANY "/pad/:id" request get-pad)
  (friend/logout
   (ANY "/logout" request (ring.util.response/redirect "/"))))

(def handler (-> #'app
                 ((fn [f]
                    (fn [req]
                      (let [r (f req)]
                        (update-in r [:headers]
                                   assoc "X-In-Like-Flynn"
                                   (str (boolean (friend/authorized? #{::authenticated}
                                                                     friend/*identity*))))))))
                 (friend/authenticate
                  {:credential-fn (partial creds/bcrypt-credential-fn users)
                   :workflows [(workflows/interactive-form)]})
                 site
                 (wrap-resource "site/")
                 wrap-bootstrap-resources))

(defn init []
  (send-off queues (fn this-fn [queues]
                     (send-off *agent* this-fn)
                     (doseq [[k v] queues]
                       (send-off *agent*
                                 (fn [queues]
                                   (let [{{:keys [cookies received]} k} queues]
                                     (when received
                                       (let [{:keys [body]} (http/get (config :compiler-url) {:cookies cookies})]
                                         (when (not= body "[\n\n]")
                                           (.put received body)))))
                                   queues)))
                     (Thread/sleep 100)
                     queues)))
