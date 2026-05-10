(ns portfolio.test-runner
  (:require [clojure.test :as test]
            [portfolio.content-test]
            [portfolio.export-test]
            [portfolio.site-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'portfolio.content-test
                                             'portfolio.export-test
                                             'portfolio.site-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
