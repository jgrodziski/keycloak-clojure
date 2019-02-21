(ns myapp.front.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [myapp.front.subs :as subs]))

(defn home-title []
  (let [name (re-frame/subscribe [::subs/name])
        user-info (re-frame/subscribe [::subs/user-info])]
    [re-com/v-box :children 
     [[re-com/title
       :label (str "Hello from " @name ", I'm "(:preferred_username @user-info)". This is the first Page.")
       :level :level1]
      [re-com/button
       :on-click (fn [] (re-frame/dispatch [:post-data "some data"]))
       :label "Post data to backend"]]]
      ))

(defn link-to-employees-page []
  [re-com/hyperlink-href
   :label "go to Employees Page"
   :href "#/employees"])

(defn home-panel []
  [re-com/v-box
   :gap "1em"
   :children [[home-title] [link-to-employees-page]]])

;; about

(defn about-title []
  [re-com/title
   :label "This is the About Page."
   :level :level1])

(defn link-to-home-page []
  [re-com/hyperlink-href
   :label "go to Home Page"
   :href "#/"])

(defn about-panel []
  [re-com/v-box
   :gap "1em"
   :children [[about-title] [link-to-home-page]]])


;; employees
(defn home-employees []
  (let [employees (re-frame/subscribe [::subs/employees])]
    [re-com/v-box :children
     [[re-com/title :label "Employees"]
      (map (fn [emp] [:p
                      (:first-name emp)
                      " "
                      (:last-name emp)]) @employees)]]))

;; referentials

;; observations

;; main

(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :employees-panel [home-employees]
    :about-panel [about-panel]
    [:div]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])]
    [re-com/v-box
     :height "100%"
     :children [[panels @active-panel]]]))
