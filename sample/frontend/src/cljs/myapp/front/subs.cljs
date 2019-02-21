(ns myapp.front.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
 ::user-info
 (fn [db]
   (:user-info db)))

(re-frame/reg-sub
 ::employees
 (fn [db] (:employees db)))

(re-frame/reg-sub
 ::referentials
 (fn [db] (:referentials db)))

(re-frame/reg-sub
 ::observations
 (fn [db] (:observations db)))
