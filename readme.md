## Convey
> What if conjure apps could communicate by simply using channels? - bnert

This library is currently **experimental**, and as such, use at your own
risk.

### Idea

There are many "transport" kinds an application may need. A non
exhaustive list is:

- Websockets
- HTTP
- TCP
- UDP
- Kafka
- Amazon SQS
- Azure Eventhub/EventGrid
- ...

Each of the "transports" above are used to convey messages in between
multiple entities. The API's around the above are varied, and the semantic
which the API's are trying to communicate have the same semantic's of queue's
(with some durablility guarantees).

With the above in mind, an ideal API/example would be:

```clojure
(ns user
  (:require
    [clojure.core.async :refer [chan go-loop <! put! closed? timeout]]
    [convey.websocket :refer [websocket]]
    [convey.sqs :refer [sqs]]))

; My gut reaction is that this is not "complecting", given the channel
; interface does not need to know about the underlying transport.
;
; In the stock `clojure.core.async`, the transport is in-memory
; message passing, and all the "connectors" are doing is shifting the
; transport to networked message passing.
;
; While this does introduce some unreliability, we can subvert
; complecting/complicating handling failures by observing the network link
; and reflecting that as the open/closed status.
;
(def wsc (chan (websocket "wss://..." 256)))
(def sqs (chan (sqs {:uri "...", :queue "myqueue", :kind :fifo})))

; process that recieves from the websocket channel, and
; pushes to the sqs channel (could also be a pipeline or async-pipeline)
(def wproc
  (go-loop []
    (when-let [message (<! wsc)]
      (put! sqs {:from "wsc", :content message}))
    (recur)))

; Monitor the channels, and restart if they have failed. Keep
; retrying until the channel is able to be opened again.
(def monitor
  (go-loop []
    (when (closed? wsc)
      (alter-var-root #'wsc (always (chan (websocket "...")))))
    (when (closed? sqs)
      (alter-var-root #'sqs (always (chan (sqs { "..." })))))

    (timeout 100) ; so as to not hog async thread pool
    (recur)))
```

The above, takes the approach of crafting buffer types (i.e. `sliding-buffer`, `dropping-buffer`)
which fulfill the Buffer interface, rather than building something on top of (i.e. wrapping)
`clojure.core.async`.

~The only "tricky" API would be HTTP, given it is inherently uni-directional
(at least for http 1.X).~


Therefore, the most likely candidates for
being able to "hook into" clojure core async are:
- HTTP (via `GET`/`POST` pair of endpoints)
- TCP (via transit/msgpack)
- UDP
- Websockets
- Kafka
- Amazon SQS (... other message services?)
- Amazon MQ
- Azure Eventhub (... other message services?)
- RabbitMQ
- IBM MQ
- ZeroMQ (really an abstraction over TCP)
- Google cloud pub/sub
- Others...?


## Roadmap

As of 08/27/2023, the following are planned to be implemented, at some point:

### Generic/Web Technologies
- [ ] TCP
- [ ] UDP
- [x] Websockets
  - status: basic example implemented
- [ ] HTTP (via long poll `GET` and `POST`)
- [ ] HTTP (via `POST`/ `SSE`)

### Dedicated Messaging Technologies
- [ ] Kafka
- [ ] MQTT
- [ ] AMQP
- [ ] Redis
- [ ] RabbitMQ


### Cloud Providers
- [ ] Azure Event Grid
- [ ] Azure Eventhub
- [ ] Azure Service Bus
- [ ] Amazon MQ
- [ ] Amazon SQS
- [ ] Amazon SNS
- [ ] AWS IoT Message Broker
- [ ] Google Cloud Pub/Sub


I'm open for contributions for others in the [idea](#idea) section. I anticipate
that this project will take some time to fully flush out, especially given
the amount of cloud providers and their individual offerings.


Additionally, the following cljs transport's shall be considered to be
on the roadmap:
- [ ] Websockets
- [ ] SSE/HTTP (i.e. readonly, with maybe an option for a `POST` endpoint to put messages to)
- [ ] HTTP (i.e. `GET`/`POST`)

