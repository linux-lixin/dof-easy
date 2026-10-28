"""Run only in local_environment.py's fresh, network-isolated MySQL namespace.

The host root and input trees are read-only. Never point this at a live database.
"""
import json
from pathlib import Path
import subprocess

client=['/inputs/mysql/usr/bin/mysql','--no-defaults','--socket=/tmp/mysql-run/mysql.sock','-uroot','-N','-B']
def sql(text):
    run=subprocess.run(client,input=text,text=True,capture_output=True,timeout=20)
    if run.returncode:
        raise RuntimeError('Disposable charts fixture failed; database output withheld')
    return run.stdout.strip()

assert sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('dnf_service','dof_native','d_taiwan','taiwan_cain','taiwan_cain_2nd')")=='0'
identity=sql('SELECT @@server_uuid')
sql("CREATE DATABASE dnf_service; CREATE TABLE dnf_service.DOF_RECHARGE_TEST_MARKER(purpose VARCHAR(80)); INSERT INTO dnf_service.DOF_RECHARGE_TEST_MARKER VALUES('disposable-recharge-contract');")
sql(Path('/inputs/dof-admin.sql').read_text())
sql("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES('dof-recharge-contract','isolated','"+identity+"','dof-native-1');")
properties=Path('/output/contract.properties')
properties.write_text('url=jdbc:mysql://127.0.0.1:33306/dnf_service?useUnicode=true&characterEncoding=UTF-8&useSSL=false\nusername=root\npassword=\nuuid='+identity+'\n')
properties.chmod(0o600)
runner=Path('/output/ChartsContracts.java')
runner.write_text('''
import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.util.*;
import java.io.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public class ChartsContracts {
 public static void main(String[] args)throws Exception {
  final int[] skipped={0};JUnitCore core=new JUnitCore();
  core.addListener(new RunListener(){public void testAssumptionFailure(Failure failure){skipped[0]++;}});
  Class<?>[] tests={
   com.aiyi.game.dnfserver.service.dof.NativeChartsContractTest.class,
   com.aiyi.game.dnfserver.service.dof.NativeChartsRewardsContractTest.class,
   com.aiyi.game.dnfserver.service.dof.NativeInheritanceContractTest.class,
   com.aiyi.game.dnfserver.service.dof.NativeRecoveryContractTest.class,
   com.aiyi.game.dnfserver.service.dof.NativeCrossBorderContractTest.class};
  Result result=core.run(tests);Map<String,Object> report=new LinkedHashMap<>();
  boolean passed=result.wasSuccessful()&&result.getRunCount()==46&&skipped[0]==0&&result.getIgnoreCount()==0;
  report.put("passed",passed);report.put("tests",result.getRunCount());report.put("failures",result.getFailureCount());
  report.put("assumptionSkips",skipped[0]);report.put("ignored",result.getIgnoreCount());
  new ObjectMapper().writeValue(new File("/output/contracts.json"),report);
  for(Failure failure:result.getFailures())System.err.println(failure.getTrace());
  if(!passed)System.exit(1);
 }
}
''')
classpath='/inputs/classes:/inputs/dependencies/*:/output'
subprocess.run(['/inputs/java/bin/javac','-cp',classpath,str(runner)],check=True)
run=subprocess.run(['/inputs/java/bin/java','-Dnet.bytebuddy.experimental=true',
    '-Ddof.recharge.contract.properties='+str(properties),'-Ddof.recharge.contract.seed=/inputs/seed.sql',
    '-cp',classpath,'ChartsContracts'],capture_output=True,text=True,timeout=240)
Path('/output/junit.log').write_text(run.stdout+'\n'+run.stderr)
summary=json.loads(Path('/output/contracts.json').read_text())
summary.update(databaseVersion=sql('SELECT VERSION()'),nativeChartsTests=10,nativeChartsRewardsTests=12,equipmentRegressionTests=24,
    fixtureSchemasRemoved=sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('dof_native','d_taiwan','taiwan_cain','taiwan_cain_2nd')")=='0',
    realServiceSql=True,syntheticDataOnly=True,serverAccessed=False,nativeGameplayVerified=False,completeReplacement=False)
Path('/output/result.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))
if run.returncode or not summary['passed'] or not summary['fixtureSchemasRemoved']:
    raise SystemExit(1)
