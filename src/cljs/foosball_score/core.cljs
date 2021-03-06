(ns foosball-score.core
  "Links the components of the frontend, and establishes the websocket handling"
  {:author "Ian McIntyre"}
  (:require
   [reagent.core :as reagent :refer [atom]]
   [secretary.core :as secretary :include-macros true]
   [accountant.core :as accountant]
   [taoensso.sente  :as sente]
   [foosball-score.game :as game]
   [foosball-score.clock :as clock]
   [foosball-score.status :as status]
   [foosball-score.events :as events]
   [foosball-score.util :refer [ws-url]]))

;; -------------------------
;; Websocket setup
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! ws-url ; Path must match the server's path
                                  {:type :auto})] ; e/o #{:auto :ajax :ws}
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))  ; Watchable, read-only atom

;; -------------------------
;; Functions

(defn new-game
  "Start a new game"
  []
  (do
    (game/new-game)
    (clock/new-game)
    (status/change-status :waiting)))

(defn ball-drop
  "Handle ball drops"
  [_]
  (when (and (not (= status/status? :playing)) (not (game/game-over?)))
    (clock/start-game!)
    (status/change-status :playing)))

;; -------------------------
;; Foosball event handlers
(defn- score-handler
  "General score handler for a team"
  [team _]
  (when (= (status/status?) :playing)
    (let [time (clock/pause-game!)]
      (game/point-for team time))
    (if (game/game-over?)
      (status/change-status :game-over)
      (status/change-status team))))

(defmethod events/foosball-event :gold
  [event]
  (score-handler :gold event))

(defmethod events/foosball-event :black
  [event]
  (score-handler :black event))

(defmethod events/foosball-event :drop
  [event]
  (ball-drop event))

;; -------------------------
;; Views

(defn home-page []
  [:div
   [game/scoreboard :black :gold]
   [clock/game-clock]
   [status/status-msg]
   [:div.scoreboard
    [:input.button {:type "button" :value "NEW GAME" :on-click new-game}]]])

;; -------------------------
;; Routes

(def page (atom #'home-page))

(defn current-page []
  [:div [@page]])

(secretary/defroute "/" []
  (reset! page #'home-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (do
    (reagent/render [current-page] (.getElementById js/document "app")))
  (sente/start-client-chsk-router! ch-chsk events/foosball-event))

(defn init! []
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (secretary/dispatch! path))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
