#!/bin/bash
# E2Eテスト実行スクリプト
# 使い方:
#   ./run_e2e.sh                    # 全テスト
#   ./run_e2e.sh ScAccountE2ETest   # sc-accountのみ
#   ./run_e2e.sh OpAccountE2ETest   # op-accountのみ
#   ./run_e2e.sh AcAccountE2ETest   # ac-accountのみ
#
# 環境変数:
#   E2E_HOST=https://sc.ddbj.nig.ac.jp  # デフォルト

MVN=/lustre12/home/oogasawa-pg/.sdkman/candidates/maven/3.9.14/bin/mvn
cd "$(dirname "$0")"

if [ -n "$1" ]; then
    $MVN compile exec:java -Dexec.args="$1"
else
    $MVN compile exec:java
fi
