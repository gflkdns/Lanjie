import asyncio
from pathlib import Path

import edge_tts


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "call-account" / "src" / "main" / "res" / "raw"

ANNOUNCEMENTS = [
    (
        "announcement_en_us.mp3",
        "en-US-JennyNeural",
        "Hello. We have detected that your phone is currently being disturbed by unwanted ads. We can help you block these ads. You can open the Caller Beacon app—the Caller Beacon app with the shield-shaped icon—and then follow the guide to enable ad blocking. If you do not need this, please ignore this message. Have a wonderful day.",
    ),
    (
        "announcement_ja_jp.mp3",
        "ja-JP-NanamiNeural",
        "こんにちは。お使いのスマートフォンが迷惑広告の影響を受けていることを検出しました。これらの広告のブロックをお手伝いできます。Caller Beacon アプリ、つまり盾のようなアイコンの Caller Beacon アプリを開き、案内に従って広告ブロック機能を有効にしてください。必要ない場合は、このメッセージを無視してください。どうぞ良い一日をお過ごしください。",
    ),
    (
        "announcement_pt_br.mp3",
        "pt-BR-FranciscaNeural",
        "Olá. Detectamos que seu celular está sendo incomodado por anúncios indesejados. Podemos ajudar você a bloquear esses anúncios. Abra o aplicativo Caller Beacon, o aplicativo Caller Beacon com o ícone em forma de escudo, e siga as instruções para ativar o bloqueio de anúncios. Se não precisar, ignore esta mensagem. Tenha um ótimo dia.",
    ),
]

GUIDE_PROMPTS = [
    (
        "phone_account_guide_en_us.mp3",
        "en-US-JennyNeural",
        "Please tap the position indicated by the hand now to turn on the switch and enable spam ad blocking.",
    ),
    (
        "phone_account_guide_ja_jp.mp3",
        "ja-JP-NanamiNeural",
        "今すぐ手が示している場所をタップしてスイッチをオンにし、迷惑広告のブロックを有効にしてください。",
    ),
    (
        "phone_account_guide_pt_br.mp3",
        "pt-BR-FranciscaNeural",
        "Toque agora no local indicado pela mão para ativar a opção e habilitar o bloqueio de anúncios indesejados.",
    ),
]


async def main() -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for file_name, voice, text in ANNOUNCEMENTS + GUIDE_PROMPTS:
        output_path = RAW_DIR / file_name
        communicate = edge_tts.Communicate(text=text, voice=voice)
        await communicate.save(str(output_path))
        print(f"generated {output_path.relative_to(ROOT)}")


if __name__ == "__main__":
    asyncio.run(main())
