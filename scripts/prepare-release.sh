#!/usr/bin/env bash
# 用法：bash scripts/prepare-release.sh <更新说明文件>
# 跑单元测试、构建签名的 release APK，校验通过后在 dist/<tag>/ 生成
# Cresto-<版本名>-<版本号>.apk、对应 .sha256 和 update.json。只在本地准备，不发布。
# 注意：assembleRelease 结束后会自动把 app/version.properties 的 VERSION_CODE +1，记得提交。
set -euo pipefail
REPO="Sure-Will/Cresto-Releases"
APP_ID="com.sure.cresto"
CERT_SHA256="81335eb1f6646f92d4f46a9b6ed555d51145f0cbfebe4f5afc626e587f928105"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
NOTES=${1:?用法: bash scripts/prepare-release.sh <更新说明文件>}
[[ -f "$NOTES" ]] || { echo "找不到更新说明：$NOTES" >&2; exit 1; }
NOTES=$(cd "$(dirname "$NOTES")" && pwd)/$(basename "$NOTES")
export JAVA_HOME=${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}
export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
fail() { echo "✗ $*" >&2; exit 1; }
cd "$ROOT"

[[ -f keystore.properties ]] || fail "缺少 keystore.properties，无法签名"
# 发布包必须能对应到一个提交；只允许上次构建留下的 version.properties 改动
DIRTY=$(git status --porcelain --untracked-files=normal | grep -v ' app/version.properties$' || true)
[[ -z "$DIRTY" || "${ALLOW_DIRTY:-}" == 1 ]] || fail "工作区有未提交改动（确实要发可设 ALLOW_DIRTY=1）：
$DIRTY"
COMMIT=$(git rev-parse HEAD)

bash ./gradlew --console=plain -q :app:testDebugUnitTest :app:assembleRelease
OUT_DIR=app/build/outputs/apk/release
APK=$OUT_DIR/$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["elements"][0]["outputFile"])' "$OUT_DIR/output-metadata.json")
[[ -f "$APK" ]] || fail "找不到 release APK：$APK"

BADGING=$("$BT/aapt2" dump badging "$APK")
PKG=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")
CODE=$(sed -n "s/^package:.* versionCode='\([0-9]*\)'.*/\1/p" <<<"$BADGING")
NAME=$(sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")
MIN_SDK=$(sed -nE "s/^(minSdkVersion|sdkVersion):'([0-9]+)'.*/\2/p" <<<"$BADGING")
NATIVE=$(sed -n "s/^native-code: //p" <<<"$BADGING")

[[ "$PKG" == "$APP_ID" ]] || fail "包名不对：$PKG"
[[ -n "$CODE" && -n "$NAME" && -n "$MIN_SDK" ]] || fail "读不到版本信息"
[[ -z "$NATIVE" || "$NATIVE" == "'arm64-v8a'" ]] || fail "原生库架构不是只有 arm64-v8a：$NATIVE"

CERTS=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p')
[[ "$CERTS" == "$CERT_SHA256" ]] || fail "签名证书不匹配：$CERTS"

TAG="v$NAME-$CODE"
# 线上最新版本号必须更低；同一个 tag 不能重发
LATEST=$(curl -fsSL "https://github.com/$REPO/releases/latest/download/update.json" 2>/dev/null \
  | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["versionCode"])' 2>/dev/null || echo 0)
(( CODE > LATEST )) || fail "versionCode $CODE 不高于线上最新 $LATEST"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  fail "$TAG 已经存在，同版本不能重发"
fi

OUT="dist/$TAG"
FILE="Cresto-$NAME-$CODE.apk"
rm -rf "$OUT" && mkdir -p "$OUT"
cp "$APK" "$OUT/$FILE"
SHA=$(shasum -a 256 "$OUT/$FILE" | cut -d' ' -f1)
SIZE=$(stat -f%z "$OUT/$FILE")
echo "$SHA  $FILE" > "$OUT/$FILE.sha256"

# 更新说明格式：第一个 "## 标题" 之前的文字是简介；"## 标题" 开始一组，"- 条目" 是组内条目。
python3 -I - "$OUT/update.json" "$NOTES" <<PY
import json, sys, datetime
out, notes_path = sys.argv[1], sys.argv[2]
types = {"新功能": "feature", "功能": "feature", "修复": "fix", "改进": "improvement", "优化": "improvement"}
intro, sections = [], []
for raw in open(notes_path, encoding="utf-8").read().splitlines():
    line = raw.strip()
    if line.startswith("## "):
        title = line[3:].strip()
        sections.append({"type": next((t for k, t in types.items() if k in title), None), "title": title, "items": []})
    elif line.startswith("- "):
        if not sections:
            sections.append({"type": None, "title": None, "items": []})
        sections[-1]["items"].append(line[2:].strip())
    elif line and not sections:
        intro.append(line)
sections = [s for s in sections if s["items"]]
if not intro and not sections:
    sys.exit("更新说明是空的")
base = "https://github.com/$REPO/releases"
manifest = {
    "schemaVersion": 1,
    "platform": "android",
    "channel": "stable",
    "applicationId": "$APP_ID",
    "versionCode": $CODE,
    "versionName": "$NAME",
    "minSdk": $MIN_SDK,
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "downloadUrl": f"{base}/download/$TAG/$FILE",
    "apkSize": $SIZE,
    "apkSha256": "$SHA",
    "releaseUrl": f"{base}/tag/$TAG",
    "sourceCommit": "$COMMIT",
    "description": {"zh": "\n".join(intro)} if intro else {},
    "releaseNotes": {"zh": sections} if sections else {},
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

cat <<MSG
✓ $TAG 已准备好：$OUT
  APK     $FILE  $SIZE 字节
  SHA-256 $SHA
  源码    $COMMIT
  app/version.properties 已自动 +1，记得提交。

发布（需要确认后再执行）：
  gh release create $TAG --repo $REPO --draft --title "Cresto $NAME ($CODE)" --notes-file "$NOTES" \\
    "$OUT/$FILE" "$OUT/$FILE.sha256" "$OUT/update.json"
  # 核对草稿里的资产大小与哈希后：
  gh release edit $TAG --repo $REPO --draft=false --latest
MSG
