#!/usr/bin/env bash

file=$1
if [ ! -s "$file" ]; then
  echo "File doesn't exist."
  exit 1
fi
new_version="$2"
append=false
while [ ! $# -eq 0 ]
do
        case "$1" in
                --append | -a)
                        append=true
                        ;;
        esac
        shift
done


if [[ -z $new_version ]]; then
  echo "No new version provided (second argument)"
  exit 3
fi
if [[ "$file" == *build.gradle ]]; then
  version=$(/./home/public/gitrunner/determineAppVersionName.sh $file)
  if [ "$append" = true ]; then
    echo "Appending $new_version"
    cmd="sed -i '/versionName \"$version\"/c\versionName \"$version$new_version\"' $file"
  else
    echo "Setting version to $new_version"
    cmd="sed -i '/versionName \"$version\"/c\versionName \"$new_version\"' $file"
  fi
  echo "Running: $cmd"
  eval $cmd
else
  echo "File can't be used to change app version"
  exit 2
fi
echo "Version is now $(/./home/public/gitrunner/determineAppVersionName.sh $file)"