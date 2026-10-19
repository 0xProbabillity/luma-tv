"""Original, deterministic UI earcons. No samples or external dependencies."""
from pathlib import Path
import math
import random
import struct
import wave

RATE = 48000
OUT = Path(__file__).resolve().parent / 'res' / 'raw'
OUT.mkdir(parents=True, exist_ok=True)


def write(name, duration, confirm=False):
    rng = random.Random(48 if confirm else 24)
    samples = []
    filtered = 0.0
    for i in range(round(RATE * duration)):
        t = i / RATE
        # Rounded onset, warm body and quiet, short glass resonance; no sharp click.
        attack = 1 - math.exp(-t / .0018)
        f = 660 if confirm else 740
        body = .58 * math.sin(2 * math.pi * f * t - .38 * math.exp(-t / .012)) * math.exp(-t / .021)
        body += .17 * math.sin(2 * math.pi * f * 2.01 * t) * math.exp(-t / .012)
        body += .10 * math.sin(2 * math.pi * f * .5 * t) * math.exp(-t / .026)
        glass = .095 * math.sin(2 * math.pi * (1320 if confirm else 1760) * t) * math.exp(-t / .037)
        filtered += .13 * (rng.uniform(-1, 1) - filtered)
        felt = filtered * .22 * math.exp(-t / .009)
        value = attack * (body + glass + felt)
        if confirm:
            u = max(0, t - .036)
            value += (1 - math.exp(-u / .003)) * .17 * math.sin(2 * math.pi * 990 * u) * math.exp(-u / .026)
        # Smoothly close the file, with headroom for overlapping fast navigation.
        value *= min(1, max(0, (duration-t)/.018)) * .42
        samples.append(value)
    path = OUT / f'{name}.wav'
    with wave.open(str(path), 'wb') as wav:
        wav.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        wav.writeframes(b''.join(struct.pack('<h', round(max(-1, min(1, s)) * 32767)) for s in samples))
    rms = math.sqrt(sum(s*s for s in samples)/len(samples))
    print(f'{path.name}: {duration*1000:.0f} ms, peak {20*math.log10(max(map(abs,samples))):.1f} dBFS, RMS {20*math.log10(rms):.1f} dBFS')


write('soft_step', .145)
write('soft_open', .195, confirm=True)
