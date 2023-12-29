#!/bin/bash

# This script is used to pack the project into a zip file.
# ignore build.sh, pack.sh, README.md, .gitignore, .git, .vscode, .DS_Store

# Get the current directory
fileuploaddemoZip=fileuploaddemo.zip
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
zip -r $fileuploaddemoZip $DIR -x $fileuploaddemoZip -x build.sh -x pack.sh -x README.md -x .gitignore -x .git -x .vscode -x .DS_Store
