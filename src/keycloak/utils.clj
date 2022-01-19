(ns keycloak.utils
  (:require    [clojure.string :as string]
               [clojure.java.io :as io]
               [clojure.pprint :as ppr]
               [me.raynes.fs :as fs])
  (:import [java.net Socket InetSocketAddress]))

(defn keycloak-running? [keycloak-client]
  (try
    (-> keycloak-client (.realm "master") (.toRepresentation) bean)
    (catch javax.ws.rs.ProcessingException pe false)
    (catch java.net.ConnectException ce false)))

(defn ns-clean
  "Remove all internal mappings from a given name space or the current one if no parameter given."
  ([] (ns-clean *ns*)) 
  ([ns] (map #(ns-unmap ns %) (keys (ns-interns ns)))))

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

(defmacro map->HashMap
  "Take a clojure map and return a java HashMap with all the keys/values put"
  [m]
  (if (symbol? m)
    `(do (let [hashmap# (java.util.HashMap.)]
           (doseq [[k# v#] ~m]
             (.put hashmap# k# v#))
           hashmap#))
    `(doto (java.util.HashMap.)
       ~@(map (fn [[k v]] `(.put ~k ~v)) ~m))))

(defmacro letdef [bindings]
  (let [pairs (partition 2 bindings)]
    `(do
       ~@(map (fn [[symbol expr]]
              `(def ~symbol ~expr)) pairs))))

(defn set-attributes
  "call setAttributes(Map<String,String>) method on representation object with a clojure map"
  [representation ^java.util.Map attributes]
  (if (and attributes (not-empty (filter some? attributes)))
    (doto representation (.setAttributes ^java.util.Map (java.util.HashMap. attributes)))
    representation))

(defn auth-server-url
  ([infra-config]
   (auth-server-url (get-in infra-config [:keycloak :protocol]) (get-in infra-config [:keycloak :host]) (get-in infra-config [:keycloak :port])))
  ([protocol host port]
   (str protocol "://" host ":" port "/auth")))

(defn parse-path "Given a file return a map with following keys: dir root base name ext, nil if file doesn't exist" [f]
  (if (fs/exists? f)
    {:dir  (.toString (fs/parent f))
     :root (.toString (last (fs/parents f)))
     :base (fs/base-name f)
     :name (fs/name f)
     :ext  (fs/extension f)}
    (throw (ex-info (format "File %s doesn't exist" f) {:file f}))))

(defn list-files
  "return a seq of File object given a directory and an optional predicate (see parse-path fn that can help to write the predicate)"
  ([dir]
  (file-seq (io/file dir)))
  ([dir pred]
   (filter pred (list-files dir))))

(defn associate-by
  "takes a vector of map and group all the map by the key in it that should be unique"
  [f coll]
  (into {} (map (juxt f identity)) coll))

(defn aggregate-keys-by-values
  "From a map of key with value a vector of values, aggregate the key by the values found in the vector.
  `(aggregate-keys-by-values {:k1 [:v1 :v2 :v3] :k2 [:v2] :k3 [:v1 :v2 :v3]})` => `{:v1 [:k1 :k3] :v2 [:k1 :k2 :k3] :v3 [:k1 :k3]}`"
  [m]
  (let [v->ks (reduce (fn [v->ks [k vs]]
                                  (reduce (fn [v->ks v]
                                            (let [ks-for-this-v (if (contains? v->ks v)
                                                                  (get v->ks v)
                                                                  (transient []))]
                                              (assoc! v->ks v (conj! ks-for-this-v k)))) v->ks vs)) (transient {}) m)]
    ;;use transient data structures for performance reason as user count can be large
    (into {} (map (fn [[k v]]
                    [k (persistent! v)]) (persistent! v->ks)))))
(aggregate-keys-by-values {:a [1 2] :b [1]})


(defn server-listening?
  "Check if a given host is listening on a given port in the limit of timeout-ms (default 500 ms)"
  ([host port]
   (server-listening? host port 500))
  ([host port timeout-ms]
   (try (let [socket (Socket.)]
          (.connect socket (InetSocketAddress. host port) timeout-ms))
        (catch Exception e
          (throw (ex-info (format "Host %s at port %s is not listening! (timeout was %d ms)" host port timeout-ms) {:host host :port port :timeout-ms timeout-ms}))))))

(defn pprint-to-file [f x]
  (binding [ppr/*print-right-margin* 600]
    (with-open [w (io/writer f :append false)]
      (ppr/pprint x w))))

(defn pprint-to-stdout [x]
  (binding [ppr/*print-right-margin* 600]
    ;;dont close stdout..
    (let [w (io/writer *out* :append false)]
      (ppr/pprint x w))))
