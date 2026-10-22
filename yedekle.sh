#!/bin/bash
cd /storage/emulated/0/MGİT/radyo
git config --global --add safe.directory /storage/emulated/0/MGİT/radyo
git init
git branch -M main
git remote add origin git@github.com:romeoakyuz/radyo.git || git remote set-url origin git@github.com:romeoakyuz/radyo.git
git add .
echo -n "Commit (yedek) adını yazın: "
read commitMessage
git commit -m "$commitMessage"
git push -u origin main --force
