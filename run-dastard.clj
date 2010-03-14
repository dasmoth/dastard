(use 'dastard :reload)
(use 'compojure)

(run-server {:port 8080} "/*" (servlet dastard-app)
                         "/das/*" (servlet-holder (new org.biojava.servlets.dazzle.DazzleServlet)
                                                  :dazzle.installation_type "net.derkholm.dastard.das.DastardDazzleInstallation"))
