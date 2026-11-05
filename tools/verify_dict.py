"""Reads a .bdic back and checks it: structure, lookups, capitalisation, next-word bigrams.

Usage: python3 -I tools/verify_dict.py app/src/main/assets/dict/en.bdic word [word...]
"""
import struct
import sys


class Bdic:
    def __init__(self, path):
        b = open(path, "rb").read()
        assert b[:4] == b"BDIC", "bad magic"
        ver, _, n, self.log_range, self.word_count = struct.unpack_from("<HHIfI", b, 4)
        assert ver == 1
        o = 20
        self.chars = struct.unpack_from(f"<{n}H", b, o); o += 2 * n
        self.parent = struct.unpack_from(f"<{n}i", b, o); o += 4 * n
        self.first = struct.unpack_from(f"<{n}i", b, o); o += 4 * n
        self.count = b[o:o + n]; o += n
        self.freq = b[o:o + n]; o += n
        self.maxf = b[o:o + n]; o += n
        self.flags = b[o:o + n]; o += n
        o = (o + 3) & ~3
        (dc,) = struct.unpack_from("<I", b, o); o += 4
        self.display = {}
        for _ in range(dc):
            node, ln = struct.unpack_from("<IH", b, o); o += 6
            self.display[node] = b[o:o + ln].decode("utf-8"); o += ln
        o = (o + 3) & ~3
        (m,) = struct.unpack_from("<I", b, o); o += 4
        self.prev = struct.unpack_from(f"<{m}I", b, o); o += 4 * m
        self.start = struct.unpack_from(f"<{m + 1}I", b, o); o += 4 * (m + 1)
        k = self.start[-1]
        self.next = struct.unpack_from(f"<{k}I", b, o); o += 4 * k
        self.cost = b[o:o + k]; o += k
        assert (o + 3) & ~3 == len(b), f"trailing bytes: {len(b) - o}"
        self.n = n
        self._check()

    def _check(self):
        words = 0
        for i in range(self.n):
            if self.freq[i]:
                words += 1
            f, c = self.first[i], self.count[i]
            if c:
                assert all(self.parent[j] == i for j in range(f, f + c)), f"child/parent mismatch at {i}"
                ch = [self.chars[j] for j in range(f, f + c)]
                assert ch == sorted(ch), f"unsorted children at {i}"
        assert words == self.word_count, (words, self.word_count)

    def find(self, key):
        n = 0
        for ch in key:
            f, c = self.first[n], self.count[n]
            n = next((j for j in range(f, f + c) if self.chars[j] == ord(ch)), None) if c else None
            if n is None:
                return None
        return n if self.freq[n] else None

    def word(self, node):
        if node in self.display:
            return self.display[node]
        s = []
        n = node
        while n > 0:
            s.append(chr(self.chars[n]))
            n = self.parent[n]
        w = "".join(reversed(s))
        fl = self.flags[node]
        if fl & 2:
            return w.upper()
        if fl & 1:
            return w[:1].upper() + w[1:]
        return w

    def following(self, node):
        import bisect
        i = bisect.bisect_left(self.prev, node)
        if i == len(self.prev) or self.prev[i] != node:
            return []
        return [(self.word(self.next[j]), self.cost[j]) for j in range(self.start[i], self.start[i + 1])]


def main():
    d = Bdic(sys.argv[1])
    print(f"OK: {d.n} nodes, {d.word_count} words, {len(d.prev)} bigram contexts, log range {d.log_range:.2f}")
    print("sentence start ->", [w for w, _ in d.following(0)[:12]])
    for w in sys.argv[2:]:
        node = d.find(w.lower())
        if node is None:
            print(f"{w!r}: not in dictionary")
            continue
        nxt = [x for x, _ in d.following(node)[:8]]
        print(f"{w!r}: display={d.word(node)!r} freq={d.freq[node]} flags={d.flags[node]} next={nxt}")


if __name__ == "__main__":
    main()
