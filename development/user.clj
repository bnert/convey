(ns user)

(require '[clojure.core.async :as a])
(require '[org.httpkit.server :as server])

; -- dev
(require '[convey.impl.websocket :as ws] :reload)

(def wsclients (atom #{}))

(defn h [req]
  #_(println 'h req)
  (server/as-channel req
    {:on-receive (fn [ch message]
                   (println "recv:" message)
                   (server/send! ch message))
     :on-open (fn [ch]
                (println "OPEN" ch)
                (swap! wsclients conj ch))
     :on-close (fn [ch _]
                 (println "CLOSE" ch)
                 (swap! wsclients disj ch))}))

(defn broadh [r]
  (server/as-channel r
    {:on-receive (fn [ch message]
                   (doseq [c (disj @wsclients ch)]
                     (server/send! c message)))
     :on-open (fn [ch]
                (swap! wsclients conj ch))
     :on-close (fn [ch _]
                (swap! wsclients disj ch))}))


(comment
  (def ws-server (server/run-server h {:port 6464}))
  (def broadcaster (server/run-server broadh {:port 6565}))

  (ws-server) ; close server
  (broadcaster)

  (def wsc
    (a/chan (ws/websocket
              {:uri     "ws://localhost:6464"
               :headers {"Authorization" "test"}})))

  (def b0 (a/chan (ws/websocket {:uri "ws://localhost:6565"})))
  (def b1 (a/chan (ws/websocket {:uri "ws://localhost:6565"})))

  (deref wsclients)

  (a/put! wsc {:all "the", :small "things"})
  (a/poll! wsc)
  (a/close! wsc)

  (a/put! b0 {:from "b0", :msg "hi"})
  (a/poll! b0)

  (a/put! b1 {:from "b1", :msg "sup"})
  (a/poll! b1)
)
