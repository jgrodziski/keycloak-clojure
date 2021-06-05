(ns reitit
  (:require [reitit.core :as rc]))

(defn reitit-extract-headers [router]
  (memoize (fn [method uri]
             (get-in (rc/match-by-path router uri)
                     [:data method :access-control :headers]))))

(defn reitit-authenticated? [router]
  (memoize (fn [method uri]
             (or (= method :options)
                 (get-in (rc/match-by-path router uri)
                         [:data method :access-control :authenticated?])))))

(defn reitit-extract-roles [router]
  (memoize (fn [method uri] (get-in (rc/match-by-path router uri) [:data method :access-control :roles]))))

