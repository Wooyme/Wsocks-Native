export JAVA_HOME='/usr/local/graalvm/19.1.1'
export JDK_HOME='/usr/local/graalvm/19.1.1'
export JRE_HOME='/usr/local/graalvm/19.1.1'
JFX_LIB=$JAVA_HOME/jre/lib

SVMBUILD=/usr/local/graalvm/19.1.1
SVMLIB=$SVMBUILD/lib

echo "JAVA_HOME="$JAVA_HOME
echo "JFX_LIB="$JFX_LIB
echo "SVMBUILD="$SVMBUILD
OLD_JAVA_HOME=$JAVA_HOME
echo 'Switching java home to:'$JAVA_HOME

PWD=$(pwd)
MYLIBS=$(echo $PWD/target/lib/*.jar | tr ' ' ':');
echo $MYLIBS
CLASSPATH="classes:$MYLIBS:$SVMBUILD/include/linux:$SVMBUILD/bin"

export PATH=$JAVA_HOME/bin:$JFX_LIB:$PATH

set -x
$SVMBUILD/bin/native-image --server-shutdown-all

echo 'JAVA_HOME='$JAVA_HOME

$SVMBUILD/bin/native-image \
--no-fallback \
--report-unsupported-elements-at-runtime \
--enable-all-security-services \
--allow-incomplete-classpath \
-H:EnableURLProtocols=http \
-H:ReflectionConfigurationFiles=netty.json \
-H:+ReportExceptionStackTraces \
-Dprism.verbose=true \
-cp $CLASSPATH \
-jar target/client-core-1.0-SNAPSHOT.jar \
-H:Name=client-core
#$--initialize-at-build-time=org.apache.log4j.Logger,\
#org.slf4j.helpers.NOPLogger,\
#org.apache.log4j.Priority,\
#org.apache.log4j.Level,\
#org.slf4j.helpers.SubstituteLoggerFactory,\
#org.slf4j.LoggerFactory,\
#org.slf4j.helpers.NOPLoggerFactory,\
#org.slf4j.helpers.Util,\
#org.apache.log4j.Log4jLoggerFactory
JAVA_HOME=$OLD_JAVA_HOME
$SVMBUILD/bin/native-image --server-shutdown-all
