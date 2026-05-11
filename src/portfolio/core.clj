(ns portfolio.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :refer [wrap-resource]]
            [portfolio.site :as site])
  (:gen-class))

(defn handler [request]
  (site/page-for-uri (:uri request)))

(def app
  (wrap-resource handler "public"))

(defn- server-port [args]
  (or (some-> (first args) parse-long)
      (some-> (System/getenv "PORT") parse-long)
      3000))

(defn -main [& args]
  (let [port (server-port args)]
    (println (str "Server listening on http://localhost:" port))
    (jetty/run-jetty #'app {:port port :join? true})))
