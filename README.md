# tg-clj

A simple-as-possible telegram bot api client inspired by [aws-api](https://github.com/cognitect-labs/aws-api).

<p>
  <a href="#installation">Installation</a> |
  <a href="#getting-started">Getting Started</a> |
  <a href="#handling-updates">Handling Updates</a> |
  <a href="https://github.com/Akeboshiwind/tg-clj-server">tg-clj-server</a>
</p>

> [!CAUTION]
> `tg-clj-server` and `tg-clj` are considered alpha!
>
> I'll put a warning in the [changelog](/CHANGELOG.md) when a breaking change happens.
> This warning will be removed once I consider the API stable.



## Why

This library gets out of the way so you can just use the [Telegram Bot API](https://core.telegram.org/bots/api) (almost) directly.

```clojure
(require '[tg-clj.core :as tg])

(def client (tg/make-client {:token "<Your bot token here>"}))

(tg/invoke client {:op :sendMessage
                   :request {:chat_id 1234 ; Replace with your chat_id
                             :text "Hello!"}})
;; => {:ok true,
;;     :result
;;     {:message_id 4321,
;;      :from
;;      {:id 123456789,
;;       :is_bot true,
;;       :first_name "My Awesome Bot",
;;       :username "mybot"},
;;      :chat
;;      {:id 987654321,
;;       :first_name "My",
;;       :last_name "Name",
;;       :username "myusername",
;;       :type "private"},
;;      :date 1709377902,
;;      :text "Hello!"}}
```



## Installation

Use as a dependency in `deps.edn` or `bb.edn`:

```clojure
io.github.akeboshiwind/tg-clj {:git/tag "v0.2.2" :git/sha "f742d7e"}
```



## Getting Started

The workflow is as simple as it gets.

First require the namespace:

```clojure
(require '[tg-clj.core :as tg])
```

Make a client (to learn how to create a bot and/or get it's token see [here](https://core.telegram.org/bots/features#botfather)):
```clojure
(def client (tg/make-client {:token "<Your bot token here>"}))
```

The browse [telegram's documentation](https://core.telegram.org/bots/api#available-methods) for a method you want to call.

Then `invoke` it as `:op`:
```clojure
(tg/invoke client {:op :getMe})
;; => {:ok true,
;;     :result
;;     {:id 123456789,
;;      :is_bot true,
;;      :first_name "My Awesome Bot",
;;      :username "mybot",
;;      :can_join_groups true,
;;      :can_read_all_group_messages true,
;;      :supports_inline_queries true}}
```

You can provide parameters using the `:request` key:
```clojure
(tg/invoke client {:op :sendMessage
                   :request {:chat_id 1234 ; Replace with your chat_id
                             :text "Hello!"}})
;; => {:ok true,
;;     :result
;;     {:message_id 4321,
;;      :from
;;      {:id 123456789,
;;       :is_bot true,
;;       :first_name "My Awesome Bot",
;;       :username "mybot"},
;;      :chat
;;      {:id 987654321,
;;       :first_name "My",
;;       :last_name "Name",
;;       :username "myusername",
;;       :type "private"},
;;      :date 1709377902,
;;      :text "Hello!"}}
```

If you provide a [`File`](https://clojuredocs.org/clojure.java.io/file) as a top level parameter then the request will be sent correctly (using `multipart/form-data`):
```clojure
(require '[clojure.java.io :as io])
(tg/invoke client {:op :sendPhoto
                   :request {:chat_id 1234
                             :photo (io/file "/path/to/my/pic.png")}})
;; => {:ok true,
;;     :result
;;     {:message_id 4321,
;;      :from
;;      {:id 123456789,
;;       :is_bot true,
;;       :first_name "My Awesome Bot",
;;       :username "mybot"},
;;      :chat
;;      {:id 987654321,
;;       :first_name "My",
;;       :last_name "Name",
;;       :username "myusername",
;;       :type "private"},
;;      :date 1709377902,
;;      :photo [ <snip> ]}}
```

Other than client errors, errors are given how telegram represents them:

```clojure
(tg/invoke client {:op :sendMessage
                   ; Oops, missing the `text` field!
                   :request {:chat_id 1234}})
;; => {:ok false,
;;     :error_code 400,
;;     :description "Bad Request: message text is empty"}
```

If you want to inspect the full response in more detail, it's attached as metadata:

```clojure
(meta (tg/invoke client {:op :getMe}))
;; => {:http-response
;;     {:opts
;;      {:as :text,
;;       :headers {"Accept" "application/json"},
;;       :method :post,
;;       :url
;;       "https://api.telegram.org/bot<your-token>/getMe"},
;;      :status 200,
;;      :headers
;;      { <snip> },
;;      :body
;;      "{\"ok\":true,\"result\":{\"id\":123456789,\"is_bot\":true,\"first_name\":\"My Awesome Bot\",\"username\":\"mybot\",\"can_join_groups\":true,\"can_read_all_group_messages\":true,\"supports_inline_queries\":true}}"}}
```

Please note that the contents of `:http-response` is an implementation detail from [`http-kit`](https://github.com/http-kit/http-kit) and may change.



## Handling updates

(Checkout [tg-clj-server](https://github.com/Akeboshiwind/tg-clj-server) if this is too "manual" for you)

The simplest way to get updates is to just invoke [`:getUpdates`](https://core.telegram.org/bots/api#getupdates) with a `timeout` (i.e. [long polling](https://en.wikipedia.org/wiki/Push_technology#Long_polling)):

```clojure
(tg/invoke client {:op :getUpdates
                   :request {:offset 0
                             :timeout 5}})
;; => {:ok true,
;;     :result
;;     [ <snip> ]}
```

A simple loop to handle basic command events might look like this:

```clojure
(defn contains-command? [u cmd]
  (when-let [text (get-in u [:message :text])]
    (let [pattern (str "^" cmd "($| )")]
      (re-find (re-pattern pattern) text))))

(defn hello-handler [u]
  (let [chat-id (get-in u [:message :chat :id])
        message-id (get-in u [:message :message_id])]
    {:op :sendMessage
     :request {:chat_id chat-id
               :text "Hello, world!"
               :reply_parameters {:message_id message-id}}}))

(loop [offset 0]
  (let [{:keys [ok result]}
        (invoke client {:op :getUpdates
                        :request {:offset offset
                                  :timeout 5}})]
    (if (and ok (seq result))
      (do (doseq [u result]
            (when (contains-command? u "/hello")
              (when-let [response (hello-handler u)]
                (invoke client response))))
          (recur (->> result (map :update_id) (apply max) inc)))
      (recur offset))))
```



## Releasing

1. Tag the commit `v<version>`
2. `git push --tags`
3. Update the README.md with the new version and git hash
4. Update the CHANGELOG.md
