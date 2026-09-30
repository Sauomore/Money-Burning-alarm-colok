"""
本地假 OpenAI 接口 —— 用来验证「烧钱闹钟」的焚烧链路，不产生任何真实费用。

用法：
    python mock_openai.py [port]

然后在 App 的设置里填：
    Base URL : http://10.0.2.2:8765      （10.0.2.2 是安卓模拟器里指向宿主机的地址）
    API Key  : 随便填，验证用
    Model    : mock-model

它会把 max_tokens 当真的吐满，这样 App 里「已烧 N tokens / ¥x.xx」的统计就能动起来。
"""

import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FILLER = "这是一段专门用来燃烧 token 的废话，没有任何信息量。"


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            req = json.loads(raw)
        except Exception:
            req = {}

        max_tokens = int(req.get("max_tokens") or 256)
        model = req.get("model", "mock-model")

        # 按 max_tokens 生成对应长度的内容，模拟「把额度吐满」
        repeats = max(1, max_tokens // len(FILLER) + 1)
        content = (FILLER * repeats)[: max_tokens * 3]

        payload = {
            "id": "chatcmpl-mock",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": model,
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "length",
                }
            ],
            "usage": {
                "prompt_tokens": 60,
                "completion_tokens": max_tokens,
                "total_tokens": max_tokens + 60,
            },
        }

        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

        print(
            f"[mock] model={model} max_tokens={max_tokens} "
            f"-> billed {max_tokens + 60} tokens",
            flush=True,
        )

    def log_message(self, fmt, *args):
        # 默认的访问日志太吵，只留错误
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    print(f"[mock] listening on 0.0.0.0:{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
