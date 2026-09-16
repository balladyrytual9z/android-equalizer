#!/bin/bash
# 全局均衡器: build-tools 工具链构建 (aapt2 compile/link + javac + d8 + zipalign + apksigner)
set -e
cd "$(dirname "$0")"

SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/34.0.0"
D8BT="$SDK/build-tools/37.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JDK=/c/users/xios/apps/jdk-21.0.12.1+1/bin
export PATH="$JDK:$PATH"

rm -rf build/obj build/out build/gen && mkdir -p build/obj build/out build/gen

echo "[1/6] aapt2 compile res"
"$BT/aapt2.exe" compile --dir res -o build/res.zip

echo "[2/6] aapt2 link"
"$BT/aapt2.exe" link -o build/out/base.apk -I "$PLATFORM" \
    --manifest AndroidManifest.xml -R build/res.zip \
    --java build/gen --auto-add-overlay \
    --min-sdk-version 28 --target-sdk-version 33

echo "[3/6] javac"
javac --release 11 -classpath "$PLATFORM" -d build/obj \
    build/gen/com/erji/geq/R.java \
    java/com/erji/geq/EqEngine.java \
    java/com/erji/geq/VerticalSlider.java \
    java/com/erji/geq/EqService.java \
    java/com/erji/geq/BootReceiver.java \
    java/com/erji/geq/MainActivity.java 2>&1 | grep -v "^warning:" || true
[ -d build/obj/com ] && echo "  compiled ok"

echo "[4/6] d8 dex"
jar cf build/out/classes-raw.jar -C build/obj .
"$JDK/java.exe" -cp "$D8BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$PLATFORM" --release --output build/out \
    build/out/classes-raw.jar

echo "[5/6] 打包 dex + zipalign"
(cd build/out && "$JDK/jar.exe" uf base.apk classes.dex)
"$BT/zipalign.exe" -f 4 build/out/base.apk build/out/aligned.apk

echo "[6/6] 签名"
if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -keystore build/debug.keystore -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner.bat" sign --ks build/debug.keystore --ks-pass pass:android \
  --out build/GlobalEQ.apk build/out/aligned.apk

ls -la build/GlobalEQ.apk
echo "完成: geq/build/GlobalEQ.apk"
