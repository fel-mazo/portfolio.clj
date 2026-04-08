(ns portfolio.content-test
  (:require [clojure.test :refer [deftest is testing]]
            [portfolio.content :as content]
            [portfolio.site :as site]))

(deftest loads-posts-by-locale
  (testing "posts are split by locale"
    (is (seq (content/posts-for-locale :fr)))
    (is (seq (content/posts-for-locale :en)))))

(deftest article-routes-exist
  (testing "known posts render to article pages"
    (doseq [post content/posts]
      (is (= 200 (:status (site/page-for-uri (:uri post))))))))
