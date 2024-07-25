#!/usr/bin/env bash

trap "exit" INT

mkdir -p .fixtures

mongorestore --drop --gzip --archive=.fixtures/all.bson.gz --nsFrom="test.stumperd_fixtures" --nsTo="testing.all" && \
  mongosh testing --eval "db.all.dropIndexes()" && \
  mongosh testing --eval "db.all.createIndex({ timestamp: 1 })"

mongorestore --drop --gzip --archive=.fixtures/results.bson.gz --nsFrom="test.stumperd_fixtures" --nsTo="testing.results" && \
  mongosh testing --eval "db.results.dropIndexes()" && \
  mongosh testing --eval "db.results.createIndex({ timestamp: 1 })"

mongorestore --drop --gzip --archive=.fixtures/questions.bson.gz --nsFrom="cs124.questioner_questions" --nsTo="testing.questions"

# vim: tw=0
