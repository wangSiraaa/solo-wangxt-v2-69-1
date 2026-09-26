#!/usr/bin/env bash
# 端到端冒烟验证：分段利率时间表的计算、校验、版本化与派生。
set -euo pipefail
BASE=http://localhost:8080/api
JQ='python3 -c'
fail() { echo "FAIL: $1"; exit 1; }

echo "== 1. 合同列表（含计划起始日） =="
curl -sf $BASE/contracts | python3 -m json.tool | head -30

echo "== 2. 合同 3 当前利率时间表（2 段） =="
curl -sf $BASE/contracts/3/rate-schedule | python3 -m json.tool

echo "== 3. 用合同 3 试算（跨调息日的期应拆分） =="
RESP=$(curl -sf -X POST $BASE/calculations/compare -H 'Content-Type: application/json' \
  -d '{"contractId":3,"prepaymentAmount":50000,"fee":100}')
echo "$RESP" | python3 -c "
import json,sys,datetime
r=json.load(sys.stdin)
assert r['rateVersionNo']==1, r['rateVersionNo']
assert len(r['rateSchedule'])==2
sched=r['comparison']['reducePayment']['schedule']
# 2027-03-15 调息；第 7 期 [2027-03-01, 2027-04-01) 跨越调息日
split=[row for row in sched if len(row.get('rateParts') or [])>1]
assert split, '应存在跨调息日的拆分期'
row=split[0]
parts=row['rateParts']
d0=datetime.date.fromisoformat(row['periodStart']); d1=datetime.date.fromisoformat(row['periodEnd'])
assert sum(p['days'] for p in parts)==(d1-d0).days, '分段天数应覆盖整期'
print('拆分期:', row['period'], row['periodStart'], '->', row['periodEnd'],
      [(p['days'], p['annualRate']) for p in parts], '利息', row['interest'])
# 汇总自洽
s=r['comparison']['reducePayment']['summary']
tot_i=round(sum(x['interest'] for x in sched),2)
assert abs(tot_i - s['totalInterest'])<0.005, (tot_i, s['totalInterest'])
assert sched[-1]['balance']==0
assert all(x['balance']>=0 for x in sched)
print('汇总一致, 末期结清为零, 无负余额 OK; recordId=', r['recordId'])
"
RECID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['recordId'])")

echo "== 4. 同日多次调整被拒绝 =="
CODE=$(curl -s -o /tmp/dup.json -w '%{http_code}' -X PUT $BASE/contracts/3/rate-schedule \
  -H 'Content-Type: application/json' \
  -d '{"segments":[{"effectiveDate":"2026-09-01","annualRate":0.036},{"effectiveDate":"2027-03-01","annualRate":0.031},{"effectiveDate":"2027-03-01","annualRate":0.032}]}')
[ "$CODE" = "400" ] || fail "同日多次调整应返回 400，实际 $CODE"
cat /tmp/dup.json; echo

echo "== 5. 空档被拒绝 =="
CODE=$(curl -s -o /tmp/gap.json -w '%{http_code}' -X PUT $BASE/contracts/3/rate-schedule \
  -H 'Content-Type: application/json' \
  -d '{"segments":[{"effectiveDate":"2027-01-01","annualRate":0.036}]}')
[ "$CODE" = "400" ] || fail "空档应返回 400，实际 $CODE"
cat /tmp/gap.json; echo

echo "== 6. 超精度被拒绝 =="
CODE=$(curl -s -o /tmp/prec.json -w '%{http_code}' -X PUT $BASE/contracts/3/rate-schedule \
  -H 'Content-Type: application/json' \
  -d '{"segments":[{"effectiveDate":"2026-09-01","annualRate":0.0361234}]}')
[ "$CODE" = "400" ] || fail "超精度应返回 400，实际 $CODE"
cat /tmp/prec.json; echo

echo "== 7. 保存新版本 v2（2027-03-01 起降为 2.9%） =="
curl -sf -X PUT $BASE/contracts/3/rate-schedule -H 'Content-Type: application/json' \
  -d '{"segments":[{"effectiveDate":"2026-09-01","annualRate":0.036},{"effectiveDate":"2027-03-01","annualRate":0.029}]}' \
  | python3 -c "import json,sys;v=json.load(sys.stdin);assert v['versionNo']==2;print('新版本 v', v['versionNo'])"

echo "== 8. 旧记录不漂移（仍为 v1 快照与结果） =="
curl -sf $BASE/calculations/$RECID | python3 -c "
import json,sys
r=json.load(sys.stdin)
assert r['rateVersionNo']==1, r['rateVersionNo']
assert abs(r['rateSchedule'][1]['annualRate']-0.031)<1e-9
print('旧记录保持 v1 快照 OK')
"

echo "== 9. 派生新计算（采用当前版本 v2，利息更低） =="
curl -sf -X POST $BASE/calculations/$RECID/derive -H 'Content-Type: application/json' -d '{}' | python3 -c "
import json,sys
r=json.load(sys.stdin)
assert r['rateVersionNo']==2, r['rateVersionNo']
assert abs(r['rateSchedule'][1]['annualRate']-0.029)<1e-9
print('派生记录 OK: recordId=', r['recordId'], '版本 v', r['rateVersionNo'],
      '降低月供总利息', r['comparison']['reducePayment']['summary']['totalInterest'])
"

echo "== 10. 手工录入 + 分段利率（期中调息拆分核对） =="
curl -sf -X POST $BASE/calculations/compare -H 'Content-Type: application/json' -d '{
  "method":"EQUAL_PRINCIPAL","scheduleStartDate":"2026-01-01",
  "rateSchedule":[{"effectiveDate":"2026-01-01","annualRate":0.06},{"effectiveDate":"2026-02-15","annualRate":0.03}],
  "remainingPrincipal":120000,"remainingPeriods":12,"prepaymentAmount":12000,"fee":0}' | python3 -c "
import json,sys
r=json.load(sys.stdin)
assert r['rateVersionNo'] is None
row=r['comparison']['reducePayment']['schedule'][1]
assert len(row['rateParts'])==2, row
assert abs(row['interest']-371.25)<0.005, row['interest']  # 99,000 × 0.00375
print('手工分段试算 OK: 第2期利息', row['interest'], '(14天×6% + 14天×3%)')
"

echo "== 11. 历史列表含利率版本 =="
curl -sf $BASE/calculations | python3 -c "
import json,sys
rows=json.load(sys.stdin)
assert any(r['rateVersionNo']==2 for r in rows), rows
assert any(r['rateVersionNo']==1 for r in rows)
print('历史记录', len(rows), '条，版本标记 OK')
"
echo "ALL SMOKE TESTS PASSED"
