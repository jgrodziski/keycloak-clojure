(ns keycloak.utils
  (:require    [clojure.string :as string]))

(defmacro hint-typed-doto
  "Evaluates x then calls all of the methods and functions with the
  value of x supplied at the front of the given arguments.  The forms
  are evaluated in order.  Returns x.
  (doto (new java.util.HashMap) (.put \"a\" 1) (.put \"b\" 2))"
  {:added "1.0"}
  [type x & forms]
  (let [gx (gensym)]
    `(let [~gx ~(with-meta x {:tag type})]
       ~@(map (fn [f]
                (with-meta
                  (if (seq? f)
                    `(~(first f) ~gx ~@(next f))
                    `(~f ~gx))
                  (meta f)))
              forms)
       ~gx)))

(defmacro setters
  "Given a compile-time literal map of attributes and values, return a function
  that calls the corresponding setters on some java object."
  [m type]
  (when-not (map? m)
    (throw (ex-info "m must be a literal map, not a symbol" {})))
  (let [capitalize (fn [coll] (map string/capitalize coll))
        camel-case (fn [kw] (-> (name kw) (string/split #"\W") capitalize string/join))
        setter-sym (fn [kw] (->> (camel-case kw) (str ".set") symbol))
        expanded (map (fn [[a val]]
                        (if (vector? val)
                          `( ~(setter-sym a) ~@val)
                          `( ~(setter-sym a) ~val)))
                      m)]
    `(fn [obj#] (hint-typed-doto ~type obj# ~@expanded))))


(defn auth-server-url
  ([infra-config]
   (auth-server-url (get-in infra-config [:keycloak :protocol]) (get-in infra-config [:keycloak :host]) (get-in infra-config [:keycloak :port])))
  ([protocol host port]
   (str protocol "://" host ":" port "/auth")))
