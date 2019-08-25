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
-H:ReflectionConfigurationFiles=netty.json \
-H:+ReportExceptionStackTraces \
-cp $CLASSPATH \
-jar target/center-1.0.0-SNAPSHOT.jar \
-H:Name=center \

JAVA_HOME=$OLD_JAVA_HOME
$SVMBUILD/bin/native-image --server-shutdown-all
