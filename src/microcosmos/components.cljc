(ns microcosmos.components)

(defn create [components key message]
  (let [component (get components key)]
    (component {:message message})))

(defn- create-mocked [mocks]
  (fn [components key message]
    (let [component (get components key)
          mocked (get-in mocks [:mocks key])]

      (when-not component
        (throw (ex-info "Didn't find component"
                        {:name key
                         :existing-components (keys components)})))
      (or mocked (component {:mocked true :message message})))))


(defn mocked* [mocks fun]
  (with-redefs [create (create-mocked mocks)]
    (fun)))

(defmacro mocked [ & body]
  (let [[possible-mocks & rest] body
        mocks (when (map? possible-mocks) possible-mocks)
        body (if mocks rest body)]
    `(mocked* ~mocks (fn [] ~@body))))
