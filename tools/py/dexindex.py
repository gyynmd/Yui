"""Builds a class/method/field index for a set of dex files and checks hook targets against it."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dexquery import Dex  # noqa: E402


def build(dex_dir, cache):
    index = {}
    for name in sorted(os.listdir(dex_dir)):
        if not name.endswith(".dex"):
            continue
        path = os.path.join(dex_dir, name)
        try:
            dex = Dex(path)
            for descriptor, superclass, _access, data_off in dex.classes():
                methods = []
                fields = []
                if data_off:
                    try:
                        fields, methods = dex.class_members(data_off)
                        methods = [
                            {"name": m[0], "ret": m[1], "params": m[2], "access": m[3]}
                            for m in methods
                        ]
                        fields = [{"name": f[0], "type": f[1], "access": f[2]} for f in fields]
                    except Exception as exc:  # noqa: BLE001
                        print(f"!! {name} {descriptor}: {type(exc).__name__}: {exc}", file=sys.stderr)
                index.setdefault(
                    descriptor,
                    {"dex": name, "super": superclass, "methods": methods, "fields": fields},
                )
        except Exception as exc:  # noqa: BLE001
            print(f"!! {name}: {type(exc).__name__}: {exc}", file=sys.stderr)
    with open(cache, "w", encoding="utf-8") as f:
        json.dump(index, f)
    return index


def find_method(index, descriptor, method, depth=0):
    entry = index.get(descriptor)
    if entry is None or depth > 12:
        return None, None
    for m in entry["methods"]:
        if m["name"] == method:
            return m, entry
    if entry.get("super"):
        return find_method(index, entry["super"], method, depth + 1)
    return None, None


def main():
    dex_dir, cache, targets_file = sys.argv[1], sys.argv[2], sys.argv[3]
    index = (
        build(dex_dir, cache)
        if not os.path.exists(cache)
        else json.load(open(cache, encoding="utf-8"))
    )
    print(f"indexed classes: {len(index)}")

    for item in json.load(open(targets_file, encoding="utf-8")):
        cls = item["class"]
        desc = "L" + cls.replace(".", "/") + ";"
        entry = index.get(desc)
        if entry is None:
            print(f"MISSING CLASS  {cls}")
            continue
        if "method" not in item:
            names = sorted({m["name"] for m in entry["methods"]})
            print(f"OK CLASS       {cls}  [{entry['dex']}] methods={names[:10]}")
            continue
        method, owner = find_method(index, desc, item["method"])
        if method is None:
            names = sorted({m["name"] for m in entry["methods"]})
            print(f"MISSING METHOD {cls}#{item['method']}  [{entry['dex']}] own={names[:12]}")
        else:
            inherited = "" if owner is entry else f" (inherited from {owner['dex']})"
            print(
                f"OK             {cls}#{item['method']}  {method['params']} -> {method['ret']}{inherited}"
            )


if __name__ == "__main__":
    main()
