# physai-isco-6310 — 自給作物農業者（ISCO 6310）の記録・物流調整を担うロボット の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-6310`、ISCO 6310 自給作物農業者）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 記録・物流調整ロボットが、世帯の作業編成、植え付け・収穫・収量の記録、種子・農具の発注調整を行う（農作業そのものも作付けの判断もしない）。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:supplies-road-to-household-store` | transport | 届いた種子・農具の袋を道路の荷下ろし場所から土の農道 300 m 先の世帯の倉へ運ぶ | 1 区間の所要時間 | 600 s（estimate） |
| `:seed-bag-onto-store-shelf` | manipulator | 種子袋を荷台から倉の棚（床の湿気を避ける高さ）へ持ち上げる | 肩関節ピークトルク | 120 N·m（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test/subsistencefarm/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。

## 測って分かったこと・限界（成長の第一候補）

1. **農道の搬送**: 積荷 10〜80 kg で所要時間は 377.4 s のまま、120 kg で 379.2 s（駆動力 120 N が効き始める）。限界 600 s を超える積荷は **約 153 kg**（その先はほぼ停止）。エネルギーは 10.6 kJ → 30.0 kJ。
2. **棚上げアーム**: 肩トルクは積荷 2 kg で 44.7 N·m、10 kg で 97.5 N·m、25 kg で 197.1 N·m。限界 120 N·m に達する積荷は **13.39 kg**。25 kg の種子袋は分けて運ぶ必要がある。
3. **estimate のままの値**: 1 回の搬送時間 600 s（荷を道端に置いておける時間の聞き取りで置き換える）、肩トルク上限 120 N·m（アームの仕様書で置き換える）、土の農道の転がり抵抗係数 0.06、アームの寸法・質量。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-6310 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-6310 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
