#!/bin/sh
#
# Gradle wrapper script for POSIX systems.
#
GRADLE_APP_NAME="Gradle"

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Find the project dir
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)

exec "$JAVACMD" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
