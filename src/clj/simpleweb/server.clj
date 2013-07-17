(ns simpleweb.server
  (:use compojure.core
        [clojure.tools.logging :only [info debug warn error]]
        ;; for view
        [hiccup.core :only [html]]
        [hiccup.page :only [html5 include-css include-js]])
  (:require [org.httpkit.server :as server]
            [clojure.string :as str]
            [clojure.tools.nrepl.server :as nrepl]
            [cemerick.shoreleave.rpc :refer (defremote) :as rpc]
            [ring.middleware.reload :as reload]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [taoensso.carmine :as car]))

;; state
(defonce prod? (atom (System/getenv "LEIN_NO_DEV")))
(defonce counter (atom 0))

;; A Redis connection with Carmine
(defn conn-spec []
  (let [url (System/getenv "REDISCLOUD_URL")]
  (if-not (str/blank? url)
    (car/make-conn-spec :uri url)
    (car/make-conn-spec))))

(defonce redis-pool (car/make-conn-pool))
(def redis-server-spec (conn-spec))
(defmacro with-car [& body] `(car/with-conn redis-pool redis-server-spec ~@body))

;; templates
(defn index []
  (html
    [:head
     [:title "Simple Clojure Web App"]
     (include-css "//netdna.bootstrapcdn.com/twitter-bootstrap/2.2.1/css/bootstrap-combined.min.css"
                  "/css/styles.css")]
    [:body
     [:div.navbar.navbar-inverse.navbar-fixed-top
      [:div.navbar-inner
       [:div.container
        [:a.brand {:href "/"} "Simple Web"]
        [:div.nav-collapse.collapse
         [:ul.nav
          [:li [:a {:href "#"} "Link 1"]
          [:li [:a {:href "#"} "Link 2"]]]]]]]]
      [:div.container
       [:h1 "Boostrap Starter Template"]
       ; testing redis integration
       [:div (str (with-car (car/incr "test-id")) " ring(s) to rule them all.")]
       [:p "Use this document as a way to quick start any new project.<br> All you get is this message and a barebones HTML document."]]
     (include-js "//ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js")
     (include-js "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.1/js/bootstrap.min.js")
     (include-js "/js/cljs.js")
     ]))

;; handler

; remotes

; routes
(defroutes app-routes
  (GET "/" [] (index))
  (route/resources "/")
  (route/not-found "Not Found"))

(def all-routes (rpc/wrap-rpc app-routes))

(def app
  (if @prod?
    (handler/site all-routes)
    (reload/wrap-reload (handler/site all-routes))))

;; init
(defn start-nrepl-server [port]
  (info "Starting nrepl server on port" port)
  (defonce server (nrepl/start-server :port port)))

(defn start-app [port]
  (info "Starting server on port" port)
  (server/run-server app {:port port :join? false}))

(defn -main [& args]
  (when-not @prod? (start-nrepl-server 7888))
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (start-app port)))
