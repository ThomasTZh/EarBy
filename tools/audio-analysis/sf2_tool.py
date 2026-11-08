#!/usr/bin/env python3
"""Inspect and subset a SoundFont 2 file.

The app bundles a piano-only subset instead of the full ~206 MiB source font, so this module
parses the RIFF/SF2 chunk structure directly: it lists presets with their instrument and sample
counts, reports which samples a preset actually references, and can write a new SF2 that keeps
only those samples. Parsing is explicit rather than delegated so the bundled subset can be
reproduced from the recorded upstream file.
"""

from __future__ import annotations

import argparse
import json
import struct
from dataclasses import dataclass
from pathlib import Path

# SF2 generator operators used by this tool.
GEN_KEY_RANGE = 43
GEN_SAMPLE_ID = 53
GEN_INSTRUMENT = 41
GEN_END_OPER = 0
SF2_SAMPLE_HEADER_SIZE = 46
SF2_BAG_SIZE = 4
SF2_PRESET_HEADER_SIZE = 38
SF2_INSTRUMENT_SIZE = 22


class SoundFontError(Exception):
    """Raised when a file is not a readable SoundFont 2 bank."""


@dataclass
class Sample:
    name: str
    start: int
    end: int
    start_loop: int
    end_loop: int
    sample_rate: int
    original_key: int
    correction: int
    link: int
    sample_type: int


@dataclass
class Instrument:
    name: str
    sample_indices: list[int]
    key_range: tuple[int, int] | None = None


@dataclass
class Preset:
    name: str
    bank: int
    program: int
    instrument_indices: list[int]


def _read_chunks(data: bytes) -> dict[str, bytes]:
    if data[:4] != b"RIFF" or data[8:12] != b"sfbk":
        raise SoundFontError("not a RIFF/SF2 file")
    declared = struct.unpack_from("<I", data, 4)[0] + 8
    if declared != len(data):
        raise SoundFontError(f"RIFF size {declared} disagrees with file size {len(data)}")

    chunks: dict[str, bytes] = {}
    offset = 12
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4].decode("ascii", "replace")
        size = struct.unpack_from("<I", data, offset + 4)[0]
        payload = data[offset + 8:offset + 8 + size]
        if chunk_id == "LIST":
            list_type = payload[:4].decode("ascii", "replace")
            sub = payload[4:]
            inner = 0
            while inner + 8 <= len(sub):
                sub_id = sub[inner:inner + 4].decode("ascii", "replace")
                sub_size = struct.unpack_from("<I", sub, inner + 4)[0]
                chunks[f"{list_type}:{sub_id}"] = sub[inner + 8:inner + 8 + sub_size]
                inner += 8 + sub_size + (sub_size % 2)
        else:
            chunks[chunk_id] = payload
        offset += 8 + size + (size % 2)
    return chunks


class SoundFont:
    """Read-only view of one SF2 file."""

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.data = self.path.read_bytes()
        self.chunks = _read_chunks(self.data)
        self.presets = self._read_presets()
        self.instruments = self._read_instruments()
        self.samples = self._read_samples()
        self.sample_data = self.chunks.get("sdta:smpl", b"")

    # --- section parsing -------------------------------------------------
    def _read_presets(self) -> list[Preset]:
        phdr = self.chunks.get("pdta:phdr")
        pbag = self.chunks.get("pdta:pbag")
        pgen = self.chunks.get("pdta:pgen")
        if phdr is None or pbag is None or pgen is None:
            raise SoundFontError("file has no preset section")
        header_count = len(phdr) // SF2_PRESET_HEADER_SIZE
        bag_count = len(pbag) // SF2_BAG_SIZE
        first_bags = [struct.unpack_from("<H", phdr, i * SF2_PRESET_HEADER_SIZE + 24)[0]
                      for i in range(header_count)]
        presets: list[Preset] = []
        for index in range(header_count - 1):  # the last record is the terminator
            base = index * SF2_PRESET_HEADER_SIZE
            name = phdr[base:base + 20].split(b"\x00")[0].decode("latin-1")
            preset, bank = struct.unpack_from("<HH", phdr, base + 20)
            # wPresetBagNdx points at this preset's first zone; the preset owns every zone up to
            # the next preset's first zone, which is where its instrument references live.
            zone_start = first_bags[index]
            zone_end = first_bags[index + 1]
            instruments: list[int] = []
            for bag in range(zone_start, min(zone_end, bag_count - 1)):
                gen_start = struct.unpack_from("<H", pbag, bag * SF2_BAG_SIZE)[0]
                gen_end = struct.unpack_from("<H", pbag, (bag + 1) * SF2_BAG_SIZE)[0]
                for gen in range(gen_start, gen_end):
                    operator, amount = struct.unpack_from("<HH", pgen, gen * 4)
                    if operator == GEN_INSTRUMENT:
                        instruments.append(amount)
            presets.append(Preset(name=name, bank=bank, program=preset, instrument_indices=instruments))
        return presets

    def _read_instruments(self) -> list[Instrument]:
        inst = self.chunks.get("pdta:inst")
        ibag = self.chunks.get("pdta:ibag")
        igen = self.chunks.get("pdta:igen")
        if inst is None or ibag is None or igen is None:
            raise SoundFontError("file has no instrument section")
        header_count = len(inst) // SF2_INSTRUMENT_SIZE
        bag_count = len(ibag) // SF2_BAG_SIZE
        first_bags = [struct.unpack_from("<H", inst, i * SF2_INSTRUMENT_SIZE + 20)[0]
                      for i in range(header_count)]
        instruments: list[Instrument] = []
        for index in range(header_count - 1):
            base = index * SF2_INSTRUMENT_SIZE
            name = inst[base:base + 20].split(b"\x00")[0].decode("latin-1")
            zone_start = first_bags[index]
            zone_end = first_bags[index + 1]
            sample_indices: list[int] = []
            key_range: tuple[int, int] | None = None
            for bag in range(zone_start, min(zone_end, bag_count - 1)):
                gen_start = struct.unpack_from("<H", ibag, bag * SF2_BAG_SIZE)[0]
                gen_end = struct.unpack_from("<H", ibag, (bag + 1) * SF2_BAG_SIZE)[0]
                for gen in range(gen_start, gen_end):
                    operator, amount = struct.unpack_from("<HH", igen, gen * 4)
                    if operator == GEN_SAMPLE_ID:
                        sample_indices.append(amount)
                    elif operator == GEN_KEY_RANGE and amount != 0 and key_range is None:
                        key_range = (amount & 0xFF, (amount >> 8) & 0xFF)
            instruments.append(
                Instrument(name=name, sample_indices=sample_indices, key_range=key_range),
            )
        return instruments

    def _read_samples(self) -> list[Sample]:
        shdr = self.chunks.get("pdta:shdr")
        if shdr is None:
            raise SoundFontError("file has no sample section")
        count = len(shdr) // SF2_SAMPLE_HEADER_SIZE
        samples: list[Sample] = []
        for index in range(count):
            base = index * SF2_SAMPLE_HEADER_SIZE
            name = shdr[base:base + 20].split(b"\x00")[0].decode("latin-1")
            start, end, start_loop, end_loop, rate, original_key, correction, link, sample_type = struct.unpack_from(
                "<IIIIIBbHH", shdr, base + 20,
            )
            samples.append(
                Sample(name, start, end, start_loop, end_loop, rate, original_key, correction, link, sample_type),
            )
        return samples[:-1]  # the last record is the terminator

    # --- reporting -------------------------------------------------------
    def describe_preset(self, bank: int, program: int) -> dict:
        preset = next((p for p in self.presets if p.bank == bank and p.program == program), None)
        if preset is None:
            raise SoundFontError(f"no preset for bank {bank} program {program}")
        sample_indices: set[int] = set()
        instruments = []
        for instrument_index in preset.instrument_indices:
            if instrument_index >= len(self.instruments):
                continue
            instrument = self.instruments[instrument_index]
            instruments.append({"index": instrument_index, "name": instrument.name,
                                "samples": len(instrument.sample_indices)})
            sample_indices.update(instrument.sample_indices)
        used_bytes = 0
        for index in sample_indices:
            if index < len(self.samples):
                sample = self.samples[index]
                used_bytes += (sample.end - sample.start) * 2
        return {
            "preset": {"name": preset.name, "bank": preset.bank, "program": preset.program},
            "instruments": instruments,
            "sampleCount": len(sample_indices),
            "sampleDataBytes": used_bytes,
            "sampleDataMiB": round(used_bytes / (1024 * 1024), 2),
            "fileMiB": round(self.path.stat().st_size / (1024 * 1024), 2),
        }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Inspect an SF2 file or report one preset's subset size")
    parser.add_argument("path")
    parser.add_argument("--bank", type=int, default=0)
    parser.add_argument("--program", type=int, default=0)
    parser.add_argument("--list-presets", action="store_true")
    args = parser.parse_args(argv)

    font = SoundFont(args.path)
    if args.list_presets:
        for preset in font.presets[:40]:
            print(f"bank {preset.bank:3d} program {preset.program:3d} {preset.name!r} "
                  f"instruments={len(preset.instrument_indices)}")
        print(f"total presets: {len(font.presets)}")
        return 0
    print(json.dumps(font.describe_preset(args.bank, args.program), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


# --- subsetting ----------------------------------------------------------
# Generator operators preserved when rebuilding an instrument zone. Envelope and tuning values
# materially change how a piano sounds, so they are carried over verbatim rather than replaced
# by defaults; range and sample operators are handled explicitly.
SUBSET_GENERATORS = (
    0,   # startAddrsOffset
    1,   # endAddrsOffset
    2,   # startloopAddrsOffset
    3,   # endloopAddrsOffset
    4,   # startAddrsCoarseOffset
    5,   # modLfoToPitch
    6,   # vibLfoToPitch
    7,   # modEnvToPitch
    8,   # initialFilterFc
    9,   # initialFilterQ
    10,  # modLfoToFilterFc
    11,  # modEnvToFilterFc
    12,  # endAddrsCoarseOffset
    13,  # modLfoToVolume
    15,  # chorusEffectsSend
    16,  # reverbEffectsSend
    17,  # pan
    21,  # delayModLFO
    22,  # freqModLFO
    23,  # delayVibLFO
    24,  # freqVibLFO
    25,  # delayModEnv
    26,  # attackModEnv
    27,  # holdModEnv
    28,  # decayModEnv
    29,  # sustainModEnv
    30,  # releaseModEnv
    31,  # keynumToModEnvHold
    32,  # keynumToModEnvDecay
    33,  # delayVolEnv
    34,  # attackVolEnv
    35,  # holdVolEnv
    36,  # decayVolEnv
    37,  # sustainVolEnv
    38,  # releaseVolEnv
    39,  # keynumToVolEnvHold
    40,  # keynumToVolEnvDecay
    44,  # keynum
    45,  # velocity
    51,  # coarseTune
    52,  # fineTune
    54,  # sampleModes
    56,  # scaleTuning
    57,  # exclusiveClass
    58,  # overridingRootKey
)

SF2_PRESET_ZONE_SIZE = 38


def _pack_name(name: str, length: int = 20) -> bytes:
    raw = name.encode("latin-1", "replace")[:length]
    return raw + b"\x00" * (length - len(raw))


def _instrument_zones(font: "SoundFont", instrument_index: int) -> list[list[tuple[int, int]]]:
    """Returns the generator lists of one instrument's zones."""
    inst, ibag, igen = font.chunks["pdta:inst"], font.chunks["pdta:ibag"], font.chunks["pdta:igen"]
    header_count = len(inst) // SF2_INSTRUMENT_SIZE
    first_bags = [struct.unpack_from("<H", inst, i * SF2_INSTRUMENT_SIZE + 20)[0] for i in range(header_count)]
    zones: list[list[tuple[int, int]]] = []
    for bag in range(first_bags[instrument_index], min(first_bags[instrument_index + 1], len(ibag) // 4 - 1)):
        gen_start = struct.unpack_from("<H", ibag, bag * 4)[0]
        gen_end = struct.unpack_from("<H", ibag, (bag + 1) * 4)[0]
        zones.append([struct.unpack_from("<HH", igen, k * 4) for k in range(gen_start, gen_end)])
    return zones


def subset_preset(source_path: str | Path, destination: Path, *, bank: int, program: int,
                  keep_instrument_indexes: list[int] | None = None, preset_name: str | None = None) -> dict:
    """Writes a new SF2 containing only the chosen preset, its instruments and their samples.

    `keep_instrument_indexes` selects which of the preset's instruments stay, which is how the
    default velocity layer is kept and the unused layers are dropped.
    """
    font = SoundFont(source_path)
    preset = next((p for p in font.presets if p.bank == bank and p.program == program), None)
    if preset is None:
        raise SoundFontError(f"no preset for bank {bank} program {program}")

    keep = keep_instrument_indexes if keep_instrument_indexes is not None else preset.instrument_indices
    instruments: list[int] = []
    for index in keep:
        if index not in instruments:
            instruments.append(index)

    # Zones of each kept instrument, with the sample each zone resolves to.
    new_instrument_zones: list[list[tuple[int, int]]] = []
    used_samples: list[int] = []
    for instrument_index in instruments:
        current_sample: int | None = None
        for gens in _instrument_zones(font, instrument_index):
            for operator, amount in gens:
                if operator == GEN_SAMPLE_ID:
                    current_sample = amount
            if current_sample is None:
                continue
            if current_sample not in used_samples:
                used_samples.append(current_sample)
            new_instrument_zones.append(list(gens))

    if not used_samples:
        raise SoundFontError("the selected preset references no samples")

    # Sample data is packed sample by sample so every pointer stays inside the new smpl chunk.
    sample_index_map = {old: new for new, old in enumerate(used_samples)}
    sample_payload = bytearray()
    new_sample_headers = bytearray()
    for old_index in used_samples:
        sample = font.samples[old_index]
        start = sample.start * 2
        end = sample.end * 2
        loop_start = (sample.start_loop - sample.start) * 2
        loop_end = (sample.end_loop - sample.start) * 2
        new_start = len(sample_payload) // 2
        sample_payload += font.sample_data[start:end]
        new_end = len(sample_payload) // 2
        new_sample_headers += _pack_name(sample.name)
        new_sample_headers += struct.pack(
            "<IIIIIBbHH",
            new_start, new_end,
            new_start + max(loop_start // 2, 0), new_start + max(loop_end // 2, 0),
            sample.sample_rate, sample.original_key, sample.correction, sample.link, sample.sample_type,
        )
    new_sample_headers += _pack_name("EOS") + struct.pack("<IIIIIBbHH", 0, 0, 0, 0, 0, 0, 0, 0, 0)

    # Instrument zone lists: rewrite sample references and add nothing else.
    new_ibag = bytearray()
    new_igen = bytearray()
    new_inst_records = bytearray()
    for zone_generators in new_instrument_zones:
        new_ibag += struct.pack("<HH", len(new_igen) // 4, 0)
        for operator, amount in zone_generators:
            if operator == GEN_SAMPLE_ID:
                new_igen += struct.pack("<HH", operator, sample_index_map[amount])
            elif operator == GEN_INSTRUMENT:
                continue
            else:
                new_igen += struct.pack("<HH", operator, amount)
    # One terminator zone closes the last instrument.
    new_ibag += struct.pack("<HH", len(new_igen) // 4, 0)

    zone_cursor = 0
    for instrument_index, instrument in enumerate(instruments):
        new_inst_records += _pack_name(font.instruments[instrument_index].name)
        count = sum(1 for _ in _instrument_zones(font, instrument_index))
        new_inst_records += struct.pack("<H", zone_cursor)
        zone_cursor += count
    new_inst_records += _pack_name("EOI") + struct.pack("<H", zone_cursor)

    # Preset side: keep the original preset zones so velocity splits stay intact, but each zone
    # now references one of the rebuilt instruments in order.
    pbag, pgen = font.chunks["pdta:pbag"], font.chunks["pdta:pgen"]
    phdr = font.chunks["pdta:phdr"]
    piano_index = font.presets.index(preset)
    first_bags = [struct.unpack_from("<H", phdr, i * SF2_PRESET_HEADER_SIZE + 24)[0]
                  for i in range(len(phdr) // SF2_PRESET_HEADER_SIZE)]
    zone_range = range(first_bags[piano_index], first_bags[piano_index + 1])
    new_pbag = bytearray()
    new_pgen = bytearray()
    instrument_for_zone: dict[int, int] = {}
    remap: dict[int, int] = {old: new for new, old in enumerate(instruments)}
    for bag in zone_range:
        gen_start = struct.unpack_from("<H", pbag, bag * 4)[0]
        gen_end = struct.unpack_from("<H", pbag, (bag + 1) * 4)[0]
        generators = [struct.unpack_from("<HH", pgen, k * 4) for k in range(gen_start, gen_end)]
        new_pbag += struct.pack("<HH", len(new_pgen) // 4, 0)
        for operator, amount in generators:
            if operator == GEN_INSTRUMENT:
                if amount not in remap:
                    continue
                new_pgen += struct.pack("<HH", operator, remap[amount])
            else:
                new_pgen += struct.pack("<HH", operator, amount)
    new_pbag += struct.pack("<HH", len(new_pgen) // 4, 0)

    # Preset header records follow the SF2 2.01 layout: name, program, bank, the index of the
    # preset's first zone, then library/genre/morph fields.
    new_phdr = _pack_name(preset_name or preset.name)
    new_phdr += struct.pack("<HH", program, bank)
    new_phdr += struct.pack("<H", 0)  # first preset zone
    new_phdr += struct.pack("<III", 0, 0, 0)
    # The terminator keeps the same 38-byte record size as every other preset record.
    new_phdr += _pack_name("EOP") + struct.pack("<HHHIII", 0, 0, 0, 0, 0, 0)

    def chunk(chunk_id: bytes, payload: bytes) -> bytes:
        # RIFF requires every chunk to occupy an even number of bytes. The declared size must
        # include the padding byte, otherwise a strict SoundFont reader rejects the file.
        if len(payload) % 2:
            payload += b"\x00"
        return chunk_id + struct.pack("<I", len(payload)) + payload

    pdta = b"pdta" + b"".join([
        chunk(b"phdr", new_phdr),
        chunk(b"pbag", bytes(new_pbag)),
        chunk(b"pmod", bytes(10)),  # one terminator modulator record
        chunk(b"pgen", bytes(new_pgen)),
        chunk(b"inst", bytes(new_inst_records)),
        chunk(b"ibag", bytes(new_ibag)),
        chunk(b"imod", bytes(10)),
        chunk(b"igen", bytes(new_igen)),
        chunk(b"shdr", bytes(new_sample_headers)),
    ])
    info = b"INFO" + b"".join([
        chunk(b"ifil", struct.pack("<HH", 2, 1)),
        chunk(b"isng", b"EMU8000\x00"),
        chunk(b"INAM", f"{preset_name or preset.name} (MuseScore General subset)".encode("latin-1") + b"\x00"),
        chunk(b"ICMT", b"Piano subset prepared for Passive Ear Training; see docs/audio/ASSETS.md\x00"),
    ])
    smpl = b"sdta" + chunk(b"smpl", bytes(sample_payload))

    body = b"sfbk" + chunk(b"LIST", info) + chunk(b"LIST", smpl) + chunk(b"LIST", pdta)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    return {
        "file": str(destination),
        "bytes": destination.stat().st_size,
        "mib": round(destination.stat().st_size / (1024 * 1024), 2),
        "instruments": instruments,
        "zoneCount": len(new_instrument_zones),
        "sampleCount": len(used_samples),
        "sampleDataMiB": round(len(sample_payload) / (1024 * 1024), 2),
    }
