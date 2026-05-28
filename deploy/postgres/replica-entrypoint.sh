#!/bin/bash
set -e

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  until pg_isready -h "$PRIMARY_HOST" -p 5432 -U "$REPLICATION_USER"; do
    echo "Waiting for primary postgres..."
    sleep 2
  done

  rm -rf "$PGDATA"/*
  export PGPASSWORD="$REPLICATION_PASSWORD"
  pg_basebackup -h "$PRIMARY_HOST" -D "$PGDATA" -U "$REPLICATION_USER" -v -P -R
  echo "primary_conninfo = 'host=$PRIMARY_HOST port=5432 user=$REPLICATION_USER password=$REPLICATION_PASSWORD'" >> "$PGDATA/postgresql.auto.conf"
fi

chmod 700 "$PGDATA"
exec docker-entrypoint.sh postgres
