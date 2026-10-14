"""从课程原始 SQL 提取真实关联数据；仅使用标准库，不连接数据库。"""
from pathlib import Path
import csv, hashlib, json, re

root = Path(__file__).resolve().parents[1]
source = root / "data/filmdb.sql"
out = Path(__file__).resolve().parent / "data"
digest = hashlib.sha256(source.read_bytes()).hexdigest()
manifest = out / "source.json"
if manifest.exists() and json.loads(manifest.read_text())["sha256"] == digest:
    raise SystemExit(0)

def values(text):
    # SQL 单引号以两个单引号转义；不把字符串中的逗号当作列分隔符。
    result, i = [], 0
    while i < len(text):
        while i < len(text) and text[i].isspace(): i += 1
        if text[i] == "'":
            i += 1; word = []
            while i < len(text):
                if text[i] == "'":
                    if i + 1 < len(text) and text[i + 1] == "'": word.append("'"); i += 2
                    else: i += 1; break
                else: word.append(text[i]); i += 1
            result.append("".join(word))
        else:
            end = text.find(",", i)
            if end == -1: end = len(text)
            token = text[i:end].strip()
            result.append(None if token.upper() == "NULL" else int(token)); i = end
        while i < len(text) and text[i].isspace(): i += 1
        if i < len(text):
            if text[i] != ",": raise ValueError("无法解析 SQL 值")
            i += 1
    return result

specs = {
    "countries": (["country_code", "continent"], [0, 2]),
    "movies": (["movieid", "country", "year_released"], [0, 2, 3]),
    "people": (["peopleid", "first_name", "surname"], [0, 1, 2]),
    "credits": (["movieid", "peopleid", "credited_as"], [0, 1, 2]),
}
rows = {name: [] for name in specs}
pattern = re.compile(r"INSERT INTO (countries|movies|people|credits) VALUES\((.*)\);$")
for line in source.read_text(encoding="utf-8").splitlines():
    match = pattern.fullmatch(line.strip())
    if match:
        name, body = match.groups()
        parsed = values(body)
        rows[name].append([parsed[i] for i in specs[name][1]])
assert all(rows.values())
movies = {r[0] for r in rows["movies"]}; people = {r[0] for r in rows["people"]}
countries = {r[0] for r in rows["countries"]}
assert all(r[1] in countries for r in rows["movies"])
assert all(r[0] in movies and r[1] in people for r in rows["credits"])
assert len(set(tuple(r) for r in rows["credits"])) == len(rows["credits"])
out.mkdir(exist_ok=True)
for name, (header, _) in specs.items():
    with (out / (name + ".csv")).open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, quoting=csv.QUOTE_ALL)
        writer.writerow(header)
        for row in rows[name]:
            f.write(",".join("NULL" if x is None else '"' + str(x).replace('"', '""') + '"' for x in row) + "\n")
manifest.write_text(json.dumps({"source": "data/filmdb.sql", "sha256": digest,
                               "rows": {n: len(r) for n, r in rows.items()}}, indent=2), encoding="utf-8")
print("关联 CSV 已从原始 SQL 提取，引用关系检查通过。")
