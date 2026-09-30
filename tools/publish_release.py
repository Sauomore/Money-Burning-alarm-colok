"""
创建 GitHub Release 并上传 APK 产物。

只用标准库，避免依赖 requests。Token 从环境变量 GH_TOKEN 读取
（由 PowerShell 从 git credential manager 里取出来传进来）。

用法：
    python publish_release.py <tag> <release-name> <notes-file> <apk-file>
"""

import json
import os
import sys
import urllib.error
import urllib.request

OWNER = "Sauomore"
REPO = "Money-Burning-alarm-colok"
API = "https://api.github.com"
UPLOAD = "https://uploads.github.com"


def token() -> str:
    tok = os.environ.get("GH_TOKEN", "").strip()
    if not tok:
        sys.exit("GH_TOKEN 环境变量为空")
    return tok


TOKEN = token()


def call(url: str, method: str = "GET", data: bytes | None = None,
         ctype: str = "application/json") -> tuple[int, bytes]:
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "dsh")
    if data is not None:
        req.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        print(f"[HTTP {e.code}] {url}")
        print(body[:1500])
        raise


def main() -> None:
    tag, name, notes_file, apk_file = sys.argv[1:5]

    with open(notes_file, encoding="utf-8") as f:
        notes = f.read()

    payload = json.dumps(
        {
            "tag_name": tag,
            "target_commitish": "main",
            "name": name,
            "body": notes,
            "draft": False,
            "prerelease": False,
        },
        ensure_ascii=False,
    ).encode("utf-8")

    print(f"[1/2] 创建 Release {tag} ...")
    status, raw = call(f"{API}/repos/{OWNER}/{REPO}/releases", "POST", payload)
    rel = json.loads(raw)
    rel_id = rel["id"]
    print(f"      OK  id={rel_id}")
    print(f"      {rel['html_url']}")

    apk_name = os.path.basename(apk_file)
    size_mb = os.path.getsize(apk_file) / 1024 / 1024
    print(f"[2/2] 上传 {apk_name} ({size_mb:.2f} MB) ...")

    with open(apk_file, "rb") as f:
        blob = f.read()

    # GitHub 支持直接以原始字节作为请求体上传资产，不需要拼 multipart
    url = (
        f"{UPLOAD}/repos/{OWNER}/{REPO}/releases/{rel_id}/assets"
        f"?name={urllib.parse.quote(apk_name)}"
    )
    status, raw = call(url, "POST", blob, "application/vnd.android.package-archive")
    asset = json.loads(raw)
    print(f"      OK  {asset['name']}  {asset['size'] / 1024 / 1024:.2f} MB")
    print(f"      {asset['browser_download_url']}")
    print(f"      下载数初始值: {asset['download_count']}")

    print("\n完成。")
    print(f"Release 页面: {rel['html_url']}")


if __name__ == "__main__":
    main()
