#!/bin/sh
# System-JRE launcher: mirrors the jpackage JavaOptions, minus the bundled runtime.
APP_HOME=/usr/lib/siliconplayer/app
exec java \
    -Djava.library.path="$APP_HOME" \
    -Dskiko.library.path="$APP_HOME" \
    -Dcompose.application.resources.dir="$APP_HOME/resources" \
    -Dcompose.application.configure.swing.globals=true \
    -cp "$APP_HOME/*" \
    com.flopster101.siliconplayer.desktop.MainKt "$@"
