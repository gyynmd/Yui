"""Minimal DEX reader: classes, methods and fields of one or more dex files."""
import struct
import sys


def read_uleb128(buf, pos):
    result = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, pos
        shift += 7


def mutf8_decode(buf, pos):
    _, pos = read_uleb128(buf, pos)
    out = bytearray()
    while True:
        b = buf[pos]
        if b == 0:
            pos += 1
            break
        if b < 0x80:
            out.append(b)
            pos += 1
        elif (b & 0xE0) == 0xC0:
            out.append(((b & 0x1F) << 6) | (buf[pos + 1] & 0x3F))
            pos += 2
        else:
            out.append(((b & 0x0F) << 12) | ((buf[pos + 1] & 0x3F) << 6) | (buf[pos + 2] & 0x3F))
            pos += 3
    return out.decode("utf-8", "replace"), pos


class Dex:
    def __init__(self, path):
        self.path = path
        with open(path, "rb") as f:
            self.buf = f.read()
        b = self.buf
        self.string_ids_size, self.string_ids_off = struct.unpack_from("<II", b, 56)
        self.type_ids_size, self.type_ids_off = struct.unpack_from("<II", b, 64)
        self.proto_ids_size, self.proto_ids_off = struct.unpack_from("<II", b, 72)
        self.field_ids_size, self.field_ids_off = struct.unpack_from("<II", b, 80)
        self.method_ids_size, self.method_ids_off = struct.unpack_from("<II", b, 88)
        self.class_defs_size, self.class_defs_off = struct.unpack_from("<II", b, 96)
        self._strings = {}
        self._types = {}

    def string(self, idx):
        if idx not in self._strings:
            off = struct.unpack_from("<I", self.buf, self.string_ids_off + idx * 4)[0]
            self._strings[idx] = mutf8_decode(self.buf, off)[0]
        return self._strings[idx]

    def type(self, idx):
        if idx not in self._types:
            str_idx = struct.unpack_from("<I", self.buf, self.type_ids_off + idx * 4)[0]
            self._types[idx] = self.string(str_idx)
        return self._types[idx]

    def proto(self, idx):
        _shorty, ret_type, params_off = struct.unpack_from(
            "<III", self.buf, self.proto_ids_off + idx * 12
        )
        params = []
        if params_off:
            size = struct.unpack_from("<I", self.buf, params_off)[0]
            for i in range(size):
                t = struct.unpack_from("<H", self.buf, params_off + 4 + i * 2)[0]
                params.append(self.type(t))
        return self.type(ret_type), params

    def method(self, idx):
        cls_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", self.buf, self.method_ids_off + idx * 8
        )
        ret, params = self.proto(proto_idx)
        return self.type(cls_idx), self.string(name_idx), ret, params

    def field(self, idx):
        cls_idx, type_idx, name_idx = struct.unpack_from("<HHI", self.buf, self.field_ids_off + idx * 8)
        return self.type(cls_idx), self.type(type_idx), self.string(name_idx)

    def classes(self):
        for i in range(self.class_defs_size):
            base = self.class_defs_off + i * 32
            class_idx, access, super_idx, _ifaces, _src, _annot, data_off, _static = struct.unpack_from(
                "<IIIIIIII", self.buf, base
            )
            superclass = self.type(super_idx) if super_idx != 0xFFFFFFFF else None
            yield self.type(class_idx), superclass, access, data_off

    def class_members(self, data_off):
        b = self.buf
        pos = data_off
        sf, pos = read_uleb128(b, pos)
        inf, pos = read_uleb128(b, pos)
        dm, pos = read_uleb128(b, pos)
        vm, pos = read_uleb128(b, pos)

        fields = []
        for count in (sf, inf):
            idx = 0
            for _ in range(count):
                diff, pos = read_uleb128(b, pos)
                access, pos = read_uleb128(b, pos)
                idx += diff
                _cls, ftype, fname = self.field(idx)
                fields.append((fname, ftype, access))

        methods = []
        for count in (dm, vm):
            idx = 0
            for _ in range(count):
                diff, pos = read_uleb128(b, pos)
                access, pos = read_uleb128(b, pos)
                _code, pos = read_uleb128(b, pos)
                idx += diff
                _cls, name, ret, params = self.method(idx)
                methods.append((name, ret, params, access))
        return fields, methods


if __name__ == "__main__":
    query = sys.argv[1]
    for path in sys.argv[2:]:
        try:
            dex = Dex(path)
            for descriptor, superclass, access, data_off in dex.classes():
                if query.lower() in descriptor.lower():
                    print(f"== {path} :: {descriptor}  (super={superclass}, access=0x{access:x})")
                    if data_off:
                        _fields, methods = dex.class_members(data_off)
                        for name, ret, params, maccess in methods:
                            vis = "public" if maccess & 0x1 else "private"
                            if maccess & 0x8:
                                vis += " static"
                            print(f"   {vis:14s} {name}({', '.join(params)}){ret}")
        except Exception as exc:  # noqa: BLE001
            print(f"!! {path}: {type(exc).__name__}: {exc}")
