@echo off
set APP_HOME=%~dp0
java -cp "%APP_HOME%gradle\\wrapper\\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
