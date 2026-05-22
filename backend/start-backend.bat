@echo off
cd /d C:\Users\21124\Downloads\ShopAgent-X-main\ShopAgent-X-main\backend
set JAVA_HOME=C:\DevTools\jdk-17.0.19+10
set M2_HOME=C:\DevTools\apache-maven-3.9.8
set PATH=%JAVA_HOME%\bin;%M2_HOME%\bin;%PATH%
echo Starting Spring Boot...
mvn spring-boot:run
pause
