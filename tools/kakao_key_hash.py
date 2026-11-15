"""Print the Kakao Base64(SHA-1(signing certificate)) from an APK, without a private key."""
import argparse,base64,os,pathlib,re,shutil,subprocess
p=argparse.ArgumentParser()
p.add_argument('apk',type=pathlib.Path)
p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
p.add_argument('--apksigner-jar',type=pathlib.Path)
a=p.parse_args()
jar=a.apksigner_jar
if jar is None and a.sdk:
    found=sorted(pathlib.Path(a.sdk).glob('build-tools/*/lib/apksigner.jar'))
    if found:jar=found[-1]
if jar is None:raise SystemExit('Set ANDROID_HOME, --sdk, or --apksigner-jar.')
java=pathlib.Path(os.environ.get('JAVA_HOME',''))/'bin'/('java.exe' if os.name=='nt' else 'java')
exe=str(java) if java.exists() else shutil.which('java')
if not exe:raise SystemExit('JDK 17 java was not found.')
r=subprocess.run([exe,'-jar',str(jar),'verify','--print-certs',str(a.apk)],capture_output=True,text=True,check=True)
matches=re.findall(r'Signer #\d+ certificate SHA-1 digest:\s*([a-fA-F0-9]+)',r.stdout)
if not matches:raise SystemExit('No verified signing certificate found.')
for digest in matches:print(base64.b64encode(bytes.fromhex(digest)).decode())
