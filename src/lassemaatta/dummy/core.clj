(ns lassemaatta.dummy.core
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [selmer.parser :as selmer]))

(defn id-seq
  "Lazy sequence of identifiers: a, b, c, ... y, z, aa, ab, ac, .."
  []
  (let [labels "abcdefghijklmnopqrstuvxyz"]
    (->> (map str labels)
         (iterate (fn [chars]
                    (for [x chars
                          y labels]
                      (str x y))))
         (apply concat)
         (map (fn [id] {:id id})))))

(defn make-absolute
  "Construct an absolute path to target"
  [& dirs]
  (let [absolute (fn [path]
                   (if (fs/relative? path)
                     (fs/absolutize path)
                     path))]
    (-> (apply fs/path dirs)
        (absolute)
        (fs/normalize))))

(defn add-path
  "Use the id to create a path to the file (and subdirectory)"
  [root {:keys [id] :as item}]
  (let [path (str/join "/" (butlast id))]
    (if (seq path)
      (assoc item :path (make-absolute root path)
                  :file (make-absolute root path (str (last id) ".clj")))
      (assoc item :file (make-absolute root (str id ".clj"))))))

(defn add-ns
  "Calculate the namespace for the dummy clj file.

  e.g. `\"abc\"` becomes `\"a.b.c\"`"
  [{:keys [id] :as item}]
  (let [namespace (str/join "." id)]
    (assoc item :ns-name namespace)))

(defn create-dir?
  [{:keys [id path]}]
  (boolean (and (= \a (last id))
                (some? path))))

(defn add-create-dir
  [item]
  (assoc item :create-dir? (create-dir? item)))

(defn add-content
  "Render the content template as string"
  [{:keys [ns-name] :as item}]
  (assoc item :content (selmer/render-file "templates/small_sample.clj" {:ns-name ns-name})))

(defn resolve-target-directory!
  [target-dir]
  (let [path (make-absolute target-dir)]
    (when (not (fs/exists? path))
      (throw (ex-info "Output directory does not exist" {:path path})))
    (when (not (fs/directory? path))
      (throw (ex-info "Output directory is not a directory" {:path path})))
    (when (not (fs/writable? path))
      (throw (ex-info "Output directory is not writeable" {:path path})))
    path))

(defn create-dir!
  [path]
  (when (fs/exists? path)
    (throw (ex-info "Generated directory exists already!" {:path path})))
  (println "Creating directory:" (.toString path))
  (fs/create-dir path))


(defn process-file!
  [{:keys [path content file create-dir?] :as item}]
  (when create-dir?
    (create-dir! path))
  (when (fs/exists? file)
    (throw (ex-info "Generated file exists already!" {:path file})))
  (println "Writing file:" (.toString file))
  (with-open [f (io/writer (.toFile file) :append true)]
      (.write f content)))

(defn write-source-files!
  [root count]
  (create-dir! root)
  (->> (id-seq)
       (map (partial add-path root))
       (map add-ns)
       (map add-content)
       (map add-create-dir)
       (take count)
       (run! process-file!)))

(defn copy-project-clj!
  [root]
  (let [istream (-> (io/resource "templates/project.clj")
                    (io/input-stream))
        out     (.toFile (make-absolute root "project.clj"))]
    (io/copy istream out)))

(defn create-project!
  [{:keys [target-dir count]}]
  (let [root (resolve-target-directory! target-dir)]
    (copy-project-clj! root)
    (write-source-files! (make-absolute root "src") count)))

(def cli-options
  ;; An option with a required argument
  [["-o" "--output OUTPUT" "Output target directory (must exist)"]
   ["-c" "--count COUNT"   "Number of files to create"
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn usage
  [summary]
  (->> ["Generates a dummy leiningen project."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        summary
        ""]
       (str/join \newline)))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary] :as opts}
        (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message errors :ok? false}

      (and (:output options) (:count options))
      {:target-dir (:output options)
       :count (:count options)}

      :else
      {:exit-message (usage summary) :ok? false})))

(defn -main
  [& _args]
  (let [{:keys [exit-message ok?] :as resp} (validate-args *command-line-args*)]
    (when exit-message
      (println exit-message))
    (when (some? ok?)
      (println "Exiting prematurely..")
      (System/exit (if ok? 0 1)))
    (try
      (create-project! resp)
      (catch Exception e
        (println e)))
    (println "Done")))
