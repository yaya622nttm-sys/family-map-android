# 家族マップ — Family Location Sharing App

家族だけがルームコードで参加できる、位置情報リアルタイム共有のAndroidアプリです。

---

## 機能

- **リアルタイム位置共有** — メンバーの現在地を地図上にピン表示（4秒ごと更新）
- **ルームコード方式** — 6文字のコードで家族だけが参加できる
- **カスタムピン** — メンバーごとにカラーと頭文字付きのバブルマーカー
- **バックグラウンド動作** — フォアグラウンドサービスでアプリ非表示中も位置情報を送信
- **移動履歴** — 過去7日分の軌跡をPolylineで表示、時刻スライダーで確認
- **ダークモード対応**

---

## セットアップ手順

### 1. Firebase プロジェクトの作成

1. [Firebase Console](https://console.firebase.google.com/) にアクセス
2. 「プロジェクトを追加」→ プロジェクト名を入力（例：`FamilyMap`）
3. Google アナリティクスはOFF で問題ありません

### 2. Realtime Database の有効化

1. 左サイドバー → 「構築」→「Realtime Database」
2. 「データベースを作成」をクリック
3. 場所：`asia-southeast1`（シンガポール、日本から近い）を推奨
4. セキュリティルール：「テストモードで開始」を選択して完了

5. 「ルール」タブを開き、以下のルールを貼り付けて「公開」：

```json
{
  "rules": {
    "rooms": {
      "$roomCode": {
        "members": {
          "$userId": {
            ".read": "auth == null",
            ".write": "auth == null"
          }
        },
        "history": {
          "$userId": {
            "$date": {
              ".read": "auth == null",
              ".write": "auth == null",
              "$ts": {
                ".validate": "newData.hasChildren(['lat','lng','ts'])"
              }
            }
          }
        }
      }
    }
  }
}
```

### 3. google-services.json の取得

1. Firebase Console → プロジェクトの概要 横の歯車アイコン → 「プロジェクトの設定」
2. 「マイアプリ」→「Androidアプリを追加」
3. Androidパッケージ名：`com.family.map`
4. 「アプリを登録」→「google-services.json をダウンロード」
5. ダウンロードしたファイルを `app/` フォルダに配置（既存のプレースホルダーを上書き）

### 4. Google Maps API キーの取得

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス
2. 上記と同じプロジェクトを選択（またはFirebaseが自動作成したプロジェクト）
3. 「APIとサービス」→「ライブラリ」→「Maps SDK for Android」を検索 → 有効にする
4. 「APIとサービス」→「認証情報」→「認証情報を作成」→「APIキー」
5. 作成されたAPIキーをコピー

> **推奨**: APIキーのアプリケーション制限で `com.family.map` のみ許可するよう設定してください

### 5. local.properties の設定

プロジェクトルートの `local.properties` を編集：

```properties
sdk.dir=C\:\\Users\\<あなたのユーザー名>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=ここにAPIキーを貼り付け
```

> `local.properties` は `.gitignore` に含まれているため、Gitにコミットされません

### 6. ビルドと実行

```bash
# Android Studio で開くか、コマンドラインで：
./gradlew assembleDebug

# 実機またはエミュレーターにインストール
./gradlew installDebug
```

### 7. 実機での確認

1. アプリを起動 → 名前と色を入力
2. 「新しいルームを作成」→ 6文字のルームコードが生成される
3. 他の家族のスマートフォンにも同じアプリをインストール
4. 同じルームコードを入力して「参加」
5. 地図上にお互いのピンが表示されることを確認

---

## プロジェクト構成

```
app/src/main/java/com/family/map/
├── MainActivity.kt           # エントリーポイント、ナビゲーション、テーマ
├── model/
│   ├── Member.kt             # メンバーデータクラス
│   └── LocationPoint.kt      # 位置情報データクラス
├── data/
│   ├── FirebaseRepository.kt # Firebase CRUD操作
│   ├── LocationRepository.kt # FusedLocationProviderClient ラッパー
│   └── PrefsRepository.kt    # SharedPreferences ラッパー
├── service/
│   └── LocationService.kt    # フォアグラウンドサービス（位置情報送信）
└── ui/
    ├── setup/
    │   └── SetupScreen.kt    # 初回セットアップ画面
    ├── map/
    │   ├── MapScreen.kt      # メインマップ画面
    │   └── MemberChip.kt     # メンバー選択チップ
    └── history/
        └── HistoryScreen.kt  # 移動履歴画面
```

---

## 技術スタック

| 項目 | バージョン |
|------|-----------|
| 言語 | Kotlin 1.9.23 |
| UI | Jetpack Compose (BOM 2024.04.01) |
| 地図 | Google Maps Compose 4.3.3 |
| バックエンド | Firebase Realtime Database |
| 位置情報 | FusedLocationProviderClient |
| バックグラウンド | Foreground Service |
| 最低SDK | API 26 (Android 8.0) |

---

## 注意事項

- **位置情報パーミッション**: Android 10以降はバックグラウンド位置情報の「常に許可」が必要です
- **バッテリー消費**: 30秒ごとに位置情報を送信するため、バッテリー消費が増加します
- **データ削除**: アプリ起動時に7日以上前の履歴を自動削除します
- **セキュリティ**: 現在は認証なし構成です。本番運用にはFirebase Anonymous Authの追加を推奨します
- **google-services.json** と **local.properties** は絶対にGitにコミットしないでください

---

## トラブルシューティング

| 問題 | 解決策 |
|------|--------|
| 地図が表示されない | `local.properties` の `MAPS_API_KEY` を確認 |
| ピンが更新されない | Firebase Realtime Database が有効か確認 |
| 位置情報が送信されない | 位置情報パーミッション「常に許可」を確認 |
| ビルドエラー | `google-services.json` が `app/` フォルダにあるか確認 |
