$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

Write-Host '--- JAVA ---'
& "$env:JAVA_HOME\bin\java.exe" -version

Write-Host '--- MAVEN ---'
& "$env:MAVEN_HOME\bin\mvn.cmd" -version
