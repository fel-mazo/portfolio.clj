(ns portfolio.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :refer [wrap-resource]]
            [portfolio.site :as site])
  (:gen-class))

(defn handler [request]
  (site/page-for-uri (:uri request)))

(def app
  (wrap-resource handler "public"))

(defn- server-port []
  (or (some-> (System/getenv "PORT") parse-long)
      3000))

(defn -main [& _]
  (jetty/run-jetty #'app {:port (server-port) :join? true}))
