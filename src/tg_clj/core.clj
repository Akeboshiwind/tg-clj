(ns tg-clj.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn make-client
  "Given a map of:

  :token    - The bot token (required).
              See the telegram docs for more info
  :base-url - For overriding the default base url (optional).
              For use with a [local bot api server](https://core.telegram.org/bots/api#using-a-local-bot-api-server).
              (currently untested)

  Create a new Telegram bot api client.
  "
  [{:keys [token base-url]}]
  (assert token ":token is required")
  {::token token
   ::base-url (or base-url "https://api.telegram.org")})

(defn- post
  "Perform a POST request to the Telegram API"
  [{::keys [token base-url]} method opts]
  (let [opts (-> opts
                 (assoc :as :text)
                 (assoc-in [:headers "Accept"] "application/json"))
        url (str base-url "/bot" token "/" method)
        ; TODO: Add timeout
        resp @(http/post url opts)]
    (with-meta (-> resp :body (json/parse-string true))
      {:http-response resp})))

;; TODO: Support InputStreams
;;       Need to figure out how to provide a filename
;;       otherwise Telegram will error :/
(defn- file? [x]
  (instance? java.io.File x))

(defn- request->multipart
  "Given a map of parameters, convert it to a multipart/form-data compatible format.
  Does so in such a way that telegram will understand it."
  [request]
  (->> request
       (map (fn [[k v]]
              (merge
               {:name (name k)}
               (cond
                 (string? v) {:content v}
                 (file? v) {:content v :filename (.getName v)}
                 :else {:content (json/encode v)}))))))

(defn invoke
  "Given a map of:

  :op      - the method to call
  :request - the parameters for the method

  Perform a POST request to the Telegram API.
  See the [Telegram Bot API](https://core.telegram.org/bots/api) for a list of methods.
  
  If one of the top level values in :request is a file, it will
  be sent as multipart/form-data.
  Otherwise, it will be sent as application/json."
  [client {:keys [op request]}]
  (post client (name op)
        (when request
          ; Prefer application/json, no *real* reason why
          ; ChatGPT says it's more efficient :shrug:
          (if-not (some file? (vals request))
            {:headers {"Content-Type" "application/json"}
             :body (json/encode request)}
            {:headers {"Content-Type" "multipart/form-data"}
             :multipart (request->multipart request)}))))
