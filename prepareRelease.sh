#!/usr/bin/env bash
TAG=`git tag --points-at HEAD`
SET_SENTRY_DSN=$SENTRY_DSN

# Release should be tagged
if [[ -z  $TAG  ]]; then
  echo "Release is not tagged."
  exit 1
fi

# Sentry DSN should be set
if [[ -z  $SET_SENTRY_DSN  ]]; then
  echo "Sentry DSN is not set."
  exit 1
fi