@ECHO OFF
SETLOCAL

SET BASE_DIR=%~dp0
SET WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar
SET WRAPPER_PROPERTIES=%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties
SET WRAPPER_MAIN=org.apache.maven.wrapper.MavenWrapperMain

IF NOT EXIST "%WRAPPER_PROPERTIES%" (
  ECHO distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip> "%WRAPPER_PROPERTIES%"
  ECHO wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar>> "%WRAPPER_PROPERTIES%"
)

FOR /F "tokens=1,2 delims==" %%A IN ('findstr /R "^wrapperUrl=" "%WRAPPER_PROPERTIES%"') DO (
  SET WRAPPER_URL=%%B
)

IF NOT EXIST "%WRAPPER_JAR%" (
  MKDIR "%BASE_DIR%\.mvn\wrapper" 2>NUL
  IF EXIST "%ProgramFiles%\Git\usr\bin\curl.exe" (
    "%ProgramFiles%\Git\usr\bin\curl.exe" -fsSL "%WRAPPER_URL%" -o "%WRAPPER_JAR%"
  ) ELSE (
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
  )
)

IF DEFINED JAVA_HOME (
  SET JAVA_EXEC="%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET JAVA_EXEC=java
)

%JAVA_EXEC% %JVM_OPTS% -Dmaven.multiModuleProjectDirectory=%BASE_DIR% -cp "%WRAPPER_JAR%" %WRAPPER_MAIN% %*
