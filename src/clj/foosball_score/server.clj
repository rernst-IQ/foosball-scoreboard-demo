(ns foosball-score.server
  "Server and serial startup"
  {:author "Ian McIntyre"}

  (:require 
    [foosball-score.handler :refer [app push-event]]
    [foosball-score.serial :as serial]
    [config.core :refer [env]]
    [clojure.core.async :as async :refer [<!! >!! chan go-loop]]
    [org.httpkit.server :refer [run-server]])
  (:gen-class))

(defn- ser-to-ws
  "Get event from the serial module, and push it onto the
  websocket channel. Runs forever. Invocation does not block"
  []
  (go-loop []
    (let [e (serial/get-event!)]
      (push-event e)
      (recur))))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))
        ser (str (nth args 0))]
    (if-let [baud (get args 1)]
      (serial/listen-forever-on-port ser baud)
      (serial/listen-forever-on-port ser))
    (ser-to-ws)
    (run-server app {:port port :join? false})))