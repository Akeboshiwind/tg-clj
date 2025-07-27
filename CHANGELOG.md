# Changelog

## 0.3.0

- Added a `:timeout` option to `make-client`, defaults to having no timeout (I recommend setting one)
- Throws a better error when the http client errors

## 0.2.2

- Revamp the README
- Tweak docstrings

I don't expect any more breaking changes.

## 0.2.1

- Update the docs a bit.
- Don't set a clojure version in the deps.

## 0.2.0

- Allow setting the `base-url` when creating a client. I've not personally used this but apparently you can have a [local bot api server](https://core.telegram.org/bots/api#using-a-local-bot-api-server), lmk if anyone finds this useful!
- We now assert that the token is set when creating a client:

```clojure
(tg/make-client {})
; Assert failed: :token is required
```

## 0.1.0

- Initial release with `invoke` & support for files etc
