#!/bin/bash
git pull
(cd common/; mvn clean compile install)
(cd login-server/; mvn clean compile test install assembly:single && sudo docker build -t loginserver .)
(cd chat-server/; mvn clean compile test install assembly:single && sudo docker build -t chatserver .)
(cd register-server/; mvn clean compile test install assembly:single && sudo docker build -t registerserver .)