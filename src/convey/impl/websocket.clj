(ns convey.impl.websocket
  (:require
    [clojure.core.async.impl.protocols :as impl]
    [clojure.core.async.impl.buffers :refer [fixed-buffer]])
  (:import
    [clojure.lang Counted]
    [java.net URI]
    [java.net.http HttpClient WebSocket WebSocket$Builder WebSocket$Listener]
    #_[java.nio ByteBuffer]))

(defn listener!
  ^WebSocket$Listener [{:on/keys [binary text]}]
  ;; TODO: handle frames
  ;; TODO: handle connection retry
  (reify WebSocket$Listener
    ; lifecycle
    (onClose [_this w status reason]
      (.onClose w status reason))
    (onOpen [_this w]
      ; keeps the connection open until either wrapped chan is closed
      ; or the server terminates the connection, in which case the chan
      ; will cease to recv messages.
      (.request w 1))

    ; heartbeat
    (onPing [_this w bb]
      (.onPing w bb)
      (.request w 1))
    (onPong [_this w bb]
      (.onPong w bb))

    ; data
    (onBinary [_this w bb last?]
      (when binary
        (binary (apply (vector-of :byte) (.array bb)) last?))
        (.request w 1))
    (onText [_this w cs last?]
      (when text
        (text ^String (.toString cs) last?)
        (.request w 1)))))


(defn websocket [{:keys [uri headers n buf] :as _opts}]
  (let [buf (cond
              buf
                buf ; i.e. user supplied buffer
              :else ; otherwise, we're going to provide a fixed buffer
                (fixed-buffer (or n 256)))
        ^HttpClient h (HttpClient/newHttpClient)
        ^WebSocket$Builder wsb (.newWebSocketBuilder h)
        ; I.e. apply headers
        ^WebSocket$Builder wsb
        (reduce-kv
          #(.header %1 (name %2) %3)
          wsb
          headers)
        ^WebSocket ws
        (-> wsb
            (.buildAsync
              (URI/create uri)
              ^WebSocket$Listener
              (listener!
                {:on/binary
                 (fn [b _l?]
                   (impl/add!* buf (apply str (map char b))))
                 :on/text
                 (fn [s _l?]
                   (impl/add!* buf s))}))
            deref)]
    (reify
      Counted
      (count [_this]
        (count buf))
      impl/Buffer
      (full? [_this]
        (impl/full? buf))
      (remove! [_this]
        (impl/remove! buf))
      (add!* [_this item]
        ; TODO: use transit as as default and add
        ; 
        (.sendText ws (str item) true))
      (close-buf! [_this]
        (when-not (.isInputClosed ws)
          (.abort ws))
        (impl/close-buf! buf)))))

