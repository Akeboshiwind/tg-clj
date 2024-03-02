# tg-clj

A simple-as-possible telegram bot api client inspired by [aws-api](https://github.com/cognitect-labs/aws-api).

[Babashka](https://github.com/babashka/babashka) compatible!

## Installation

Use as a dependency in `deps.edn` or `bb.edn`:

```clojure
io.github.akeboshiwind/tg-clj {:git/tag "v0.1.0" :git/sha "86370b1"}
```

## Usage

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

You can provide parameters under `:request`:
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

If you provide a [`file`](https://clojuredocs.org/clojure.java.io/file) as a top level parameter then the request will correctly be sent using `multipart/form-data`:
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

## Handling updates

The simplest way to get updates is to just invoke [`:getUpdates`](https://core.telegram.org/bots/api#getupdates) with a `timeout` (i.e. [long polling](https://en.wikipedia.org/wiki/Push_technology#Long_polling)):

```clojure
(tg/invoke client {:op :getUpdates
                   :request {:offset 0
                             :timeout 5}})
;; => {:ok true,
;;     :result
;;     [ <snip> ]}
```

A simple loop to handle command events might look like this:

```clojure
(def bot-username (-> (tg/invoke client {:op :getMe})
                      (get-in [:result :username])))

(defn valid-command? [cmd]
  (re-matches #"/[a-z0-9_]+" cmd))

(defn command? [cmd u]
  (assert (valid-command? cmd) (str "Invalid command: " cmd))
  (when-let [text (get-in u [:message :text])]
    (let [pattern (str "(^| )" cmd "($|@" bot-username "| )")]
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
        (tg/invoke client {:op :getUpdates
                           :request {:offset offset
                                     :timeout 5}})]
    (if (and ok (seq result))
      (do
        (doseq [u result]
          (when (command? "/hello" u)
            (when-let [response (hello-handler u)]
              (tg/invoke client response))))
        (recur (->> result (map :update_id) (apply max) inc)))
      (recur offset))))
```

For something more framework-y look forward to my next release!
