# AutoSplit Timer Mod（Minecraft Java 1.12.2）

**Minecraftアスレチック勢向け・自動ラップタイマー**

**マルチサーバーでも使える。
走るだけで、自動ラップ。**

> Livesplit風のタイマーを、
> 
> 
> **「自分で設定した領域に入るだけでラップタイムが記録される」**
> 
> ようにしたクライアントMODです。
> 

---

## Quick Start

AutoSplit Timer Mod は、**一度設定したら、あとは走るだけ**です。

### 最小構成での使い方

1. 画面右のReleasesからMod (autosplittimer-x.x.x.jar) をダウンロード
2. Forge 1.12.2（クライアント）に MOD を導入 ()
3. スタート・ラップ・ゴールの領域を設定
4. 走る

以下のコマンドをそのまま入力してください。

```jsx
/ast course set MyCourse
/ast seg add 0 "Start" height 1
/ast seg add 1 "Lap1"  height 0.5
/ast seg add 2 "Goal"  height 1

```

※ `seg add` は、コマンド実行地点の足元に **1×1×高さ** の判定箱を作ります。

これだけで：

- Start に入る → **自動でタイマー開始**
- Lap に入る → **自動でラップ**
- Goal に入る → **自動で終了**

**ただ走るだけ！**

---

## 基本的な使い方の流れ

1. **コースを選ぶ / 作る**
    - `/ast course set <courseName>`
    - すでに作ったコースを読み込むときは `/ast course load <courseName>`
2. **選んだコースにラップ領域を作る**
    - `/ast seg add <index> "<name>" height <h>`
    - Start / Lap / Goal を自由に配置
    - index=0がスタート、最大値がゴールになります
3. **走る**
    - Start に入ると自動開始
    - 領域に入るたび自動ラップ
    - Goal で自動終了

---

## できること

- **完全自動ラップ**
    - 手動・ホットキー操作なし
- **HUDをGUIでカスタマイズ**
    - `/ast hud edit` で編集画面を開く
    - 表示する情報のON/OFF
    - 表示順・位置・スケールの調整
    - 色のカスタマイズ
- **コース設定を共有できる**
    - `config\autosplittimer\courses\`から、JSONとして保存・配布可能
- **領域の可視化**
    - 設定した領域をワールド内に表示
- **クライアントMOD**
    - マルチサーバーでも使用可能
- **複数コースを作成・切り替え可能**
    - パルクールごとに設定を保存して切り替えできます

---

## フィードバック歓迎

このMODは現在 **開発中** です。

使ってみた感想や改善案、バグ報告などがあれば、

ぜひ DM（X: konqa_ / Discord: sasas_）で教えてください。

---

## 注意

- 非公式ツールです
- LiveSplit とは無関係です
- 現在開発中のため、仕様が変わる可能性があります
