(ns components.cid)

(defprotocol CID
  (append-cid [component cid]
              "Returns a new instance of this same component with understands CIDs
and will log/publish/save so it'll be possible to correlate transactions
between services"))
