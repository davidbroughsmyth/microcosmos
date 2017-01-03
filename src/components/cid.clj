(ns components.cid)

(defprotocol CID
  (append-cid [component cid]
              "Returns a new instance of this same component with understands CIDs
and will log/publish/save so it'll be possible to correlate transactions
between services"))

(defn generate-cid [old-cid]
  (let [upcase-chars (map char (range (int \A) (inc (int \Z))))
        digits (range 10)
        alfa-digits (cycle (map str (concat upcase-chars digits)))
        cid-gen #(apply str (take % (random-sample 0.02 alfa-digits)))]
    (if old-cid
      (str old-cid "." (cid-gen 5))
      (cid-gen 8))))
