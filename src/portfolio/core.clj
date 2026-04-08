(ns portfolio.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :refer [wrap-resource]]
            [portfolio.site :as site])
  (:gen-class))

(defn handler [request]
  (let [{:keys [status body]} (site/page-for-uri (:uri request))]
    {:status status
     :headers {"content-type" "text/html; charset=utf-8"}
     :body body}))

(def app
  (wrap-resource handler "public"))

(defn- server-port []
  (or (some-> (System/getenv "PORT") parse-long)
      3000))

(defn -main [& _]
  (jetty/run-jetty #'app {:port (server-port) :join? true}))
