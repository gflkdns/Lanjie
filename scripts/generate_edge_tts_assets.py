import asyncio
from pathlib import Path

import edge_tts


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"

ANNOUNCEMENTS = [
    (
        "announcement_zh_cn.mp3",
        "zh-CN-XiaoxiaoNeural",
        "警告,警告,您刚才收到一个境外诈骗电话,我们已经帮您自动拦截,打开 Call block 查看拦截详情",
    ),
    (
        "announcement_ja_jp.mp3",
        "ja-JP-NanamiNeural",
        "警告、警告。先ほど海外からの詐欺電話を受信しました。自動的にブロックしました。Call block を開いてブロックの詳細を確認してください。",
    ),
    (
        "announcement_ko_kr.mp3",
        "ko-KR-SunHiNeural",
        "경고, 경고. 방금 해외 사기 전화를 수신했습니다. 자동으로 차단했습니다. Call block을 열어 차단 상세 정보를 확인하세요.",
    ),
    (
        "announcement_en_us.mp3",
        "en-US-JennyNeural",
        "Warning, warning. You just received an overseas scam call. We have automatically blocked it for you. Open Call block to view the blocking details.",
    ),
]


async def main() -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for file_name, voice, text in ANNOUNCEMENTS:
        output_path = RAW_DIR / file_name
        communicate = edge_tts.Communicate(text=text, voice=voice)
        await communicate.save(str(output_path))
        print(f"generated {output_path.relative_to(ROOT)}")


if __name__ == "__main__":
    asyncio.run(main())
