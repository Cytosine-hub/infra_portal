#!/bin/bash
# Restart backend when Java files are modified
FILE_PATH="$1"

# Only restart for backend Java files
if [[ "$FILE_PATH" != *src/main/java* ]] || [[ "$FILE_PATH" != *.java ]]; then
  exit 0
fi

# Run restart in background to avoid blocking Claude Code
(
  echo "[Hook] Backend file changed: $FILE_PATH" >&2
  echo "[Hook] Restarting backend..." >&2

  # Kill existing backend processes (ignore errors)
  lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true

  # Start backend
  export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/opt/homebrew/opt/openjdk@17")
  export PATH="$JAVA_HOME/bin:$PATH"
  cd /Users/zhushihao/Projects/middleware_resource_manager

  nohup mvn spring-boot:run > /tmp/backend.log 2>&1 &
  echo "[Hook] Backend starting (PID: $!), check /tmp/backend.log" >&2
) &

exit 0
